package com.phokingfabulous;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.nio.file.*;
import java.util.Base64;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static spark.Spark.*;

public class BackendServer {
    private static final Gson gson = new Gson();
    private static final Map<String, Integer> sessions = new HashMap<>();

    public static void start() {
        Database.init();
        port(4567);

        after((req, res) -> res.type("application/json"));

        post("/auth/register", (req, res) -> {
            JsonObject body = gson.fromJson(req.body(), JsonObject.class);
            String username = body.get("username").getAsString();
            String password = body.get("password").getAsString();
            String role = body.has("role") ? body.get("role").getAsString() : "cashier";

            try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users(username,password_hash,role) VALUES(?,?,?)")) {
                ps.setString(1, username);
                ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
                ps.setString(3, role);
                ps.executeUpdate();
                res.status(201);
                return gson.toJson(Map.of("status", "ok"));
            } catch (SQLException e) {
                res.status(400);
                return gson.toJson(Map.of("error", e.getMessage()));
            }
        });

        post("/auth/login", (req, res) -> {
            JsonObject body = gson.fromJson(req.body(), JsonObject.class);
            String username = body.get("username").getAsString();
            String password = body.get("password").getAsString();
            try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(
                    "SELECT id, password_hash FROM users WHERE username=?")) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && BCrypt.checkpw(password, rs.getString("password_hash"))) {
                    String token = UUID.randomUUID().toString();
                    sessions.put(token, rs.getInt("id"));
                    return gson.toJson(Map.of("token", token));
                }
                res.status(401);
                return gson.toJson(Map.of("error", "invalid_credentials"));
            }
        });

        get("/inventory/items", (req, res) -> {
            try (Connection c = Database.get(); Statement st = c.createStatement()) {
                ResultSet rs = st.executeQuery("SELECT id,name,price,stock FROM menu_items ORDER BY id");
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("name", rs.getString("name"));
                    m.put("price", rs.getDouble("price"));
                    m.put("stock", rs.getInt("stock"));
                    list.add(m);
                }
                return gson.toJson(list);
            }
        });

        post("/inventory/items", (req, res) -> {
            Integer uid = requireAuth(req, res);
            if (uid == null) return gson.toJson(Map.of("error", "unauthorized"));
            JsonObject body = gson.fromJson(req.body(), JsonObject.class);
            try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO menu_items(name,price,stock) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, body.get("name").getAsString());
                ps.setDouble(2, body.get("price").getAsDouble());
                ps.setInt(3, body.get("stock").getAsInt());
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                int id = keys.next() ? keys.getInt(1) : -1;
                res.status(201);
                return gson.toJson(Map.of("id", id));
            }
        });

        put("/inventory/items/:id", (req, res) -> {
            Integer uid = requireAuth(req, res);
            if (uid == null) return gson.toJson(Map.of("error", "unauthorized"));
            int id = Integer.parseInt(req.params(":id"));
            JsonObject body = gson.fromJson(req.body(), JsonObject.class);
            try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(
                    "UPDATE menu_items SET name=?, price=?, stock=? WHERE id=?")) {
                ps.setString(1, body.get("name").getAsString());
                ps.setDouble(2, body.get("price").getAsDouble());
                ps.setInt(3, body.get("stock").getAsInt());
                ps.setInt(4, id);
                ps.executeUpdate();
                return gson.toJson(Map.of("status", "updated"));
            }
        });

        post("/orders", (req, res) -> {
            Integer cashierId = requireAuth(req, res);
            if (cashierId == null) return gson.toJson(Map.of("error", "unauthorized"));

            JsonObject body = gson.fromJson(req.body(), JsonObject.class);
            var items = body.getAsJsonArray("items");
            double total = 0.0;

            try (Connection c = Database.get()) {
                c.setAutoCommit(false);
                for (var el : items) {
                    JsonObject it = el.getAsJsonObject();
                    int itemId = it.get("id").getAsInt();
                    int qty = it.get("quantity").getAsInt();
                    try (PreparedStatement ps = c.prepareStatement("SELECT price, stock FROM menu_items WHERE id=?")) {
                        ps.setInt(1, itemId);
                        ResultSet rs = ps.executeQuery();
                        if (!rs.next()) throw new SQLException("item_not_found");
                        double price = rs.getDouble("price");
                        int stock = rs.getInt("stock");
                        if (stock < qty) throw new SQLException("insufficient_stock");
                        total += price * qty;
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO orders(cashier_id,total,created_at) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, cashierId);
                    ps.setDouble(2, total);
                    ps.setString(3, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    int orderId = keys.next() ? keys.getInt(1) : -1;

                    for (var el : items) {
                        JsonObject it = el.getAsJsonObject();
                        int itemId = it.get("id").getAsInt();
                        int qty = it.get("quantity").getAsInt();
                        try (PreparedStatement psItem = c.prepareStatement("SELECT price FROM menu_items WHERE id=?")) {
                            psItem.setInt(1, itemId);
                            ResultSet rs = psItem.executeQuery();
                            double price = rs.getDouble("price");
                            try (PreparedStatement ins = c.prepareStatement(
                                    "INSERT INTO order_items(order_id,menu_item_id,quantity,price) VALUES(?,?,?,?)")) {
                                ins.setInt(1, orderId);
                                ins.setInt(2, itemId);
                                ins.setInt(3, qty);
                                ins.setDouble(4, price);
                                ins.executeUpdate();
                            }
                            try (PreparedStatement updStock = c.prepareStatement("UPDATE menu_items SET stock = stock - ? WHERE id=?")) {
                                updStock.setInt(1, qty);
                                updStock.setInt(2, itemId);
                                updStock.executeUpdate();
                            }
                        }
                    }

                    c.commit();
                    res.status(201);
                    double payment = body.get("payment").getAsDouble();
                    double change = payment - total;
                    return gson.toJson(Map.of("order_id", orderId, "total", total, "change", change));
                } catch (SQLException e) {
                    c.rollback();
                    res.status(400);
                    return gson.toJson(Map.of("error", e.getMessage()));
                } finally {
                    c.setAutoCommit(true);
                }
            }
        });

        delete("/inventory/items/:id", (req, res) -> {
            Integer uid = requireAuth(req, res);
            if (uid == null) return gson.toJson(Map.of("error", "unauthorized"));
            int id = Integer.parseInt(req.params(":id"));
            try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement("DELETE FROM menu_items WHERE id=?")) {
                ps.setInt(1, id);
                int affected = ps.executeUpdate();
                return gson.toJson(Map.of("deleted", affected));
            }
        });

        get("/reports/sales/daily", (req, res) -> {
            String dateStr = Optional.ofNullable(req.queryParams("date")).orElse(LocalDate.now().toString());
            try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(
                    "SELECT SUM(total) AS revenue FROM orders WHERE substr(created_at,1,10)=?")) {
                ps.setString(1, dateStr);
                ResultSet rs = ps.executeQuery();
                double revenue = rs.getDouble("revenue");
                return gson.toJson(Map.of("date", dateStr, "revenue", revenue));
            }
        });

        get("/reports/sales/monthly", (req, res) -> {
            String month = Optional.ofNullable(req.queryParams("month")).orElse(LocalDate.now().toString().substring(0,7));
            try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(
                    "SELECT SUM(total) AS revenue FROM orders WHERE substr(created_at,1,7)=?")) {
                ps.setString(1, month);
                ResultSet rs = ps.executeQuery();
                double revenue = rs.getDouble("revenue");
                return gson.toJson(Map.of("month", month, "revenue", revenue));
            }
        });

        get("/reports/orders", (req, res) -> {
            String dateStr = Optional.ofNullable(req.queryParams("date")).orElse(LocalDate.now().toString());
            try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement(
                    "SELECT o.id, o.total, o.created_at, u.username AS cashier FROM orders o LEFT JOIN users u ON o.cashier_id=u.id WHERE substr(o.created_at,1,10)=? ORDER BY o.id DESC")) {
                ps.setString(1, dateStr);
                ResultSet rs = ps.executeQuery();
                List<Map<String,Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("total", rs.getDouble("total"));
                    m.put("created_at", rs.getString("created_at"));
                    m.put("cashier", rs.getString("cashier"));
                    list.add(m);
                }
                return gson.toJson(list);
            }
        });

        post("/employees", (req, res) -> {
            JsonObject b = gson.fromJson(req.body(), JsonObject.class);
            String username = b.get("username").getAsString();
            String password = b.get("password").getAsString();
            String role = b.has("role") ? b.get("role").getAsString() : "cashier";
            String photoBase64 = b.has("photoBase64") ? b.get("photoBase64").getAsString() : null;

            try (Connection c = Database.get()) {
                c.setAutoCommit(false);
                int userId;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(username,password_hash,role) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, username);
                    ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
                    ps.setString(3, role);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    userId = keys.next() ? keys.getInt(1) : -1;
                }

                String photoPath = null;
                if (photoBase64 != null && !photoBase64.isEmpty()) {
                    byte[] data = Base64.getDecoder().decode(photoBase64);
                    Path dir = Paths.get("uploads");
                    Files.createDirectories(dir);
                    Path out = dir.resolve("emp_" + username + "_" + System.currentTimeMillis() + ".png");
                    Files.write(out, data);
                    photoPath = out.toString();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO employees(user_id,first_name,middle_name,last_name,suffix,dob,sex,contact,address,province,city,barangay,father,mother,guardian,relation,weekly_payment,photo_path) " +
                                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, userId);
                    ps.setString(2, str(b, "firstName"));
                    ps.setString(3, str(b, "middleName"));
                    ps.setString(4, str(b, "lastName"));
                    ps.setString(5, str(b, "suffix"));
                    ps.setString(6, str(b, "dob"));
                    ps.setString(7, str(b, "sex"));
                    ps.setString(8, str(b, "contact"));
                    ps.setString(9, str(b, "address"));
                    ps.setString(10, str(b, "province"));
                    ps.setString(11, str(b, "city"));
                    ps.setString(12, str(b, "barangay"));
                    ps.setString(13, str(b, "father"));
                    ps.setString(14, str(b, "mother"));
                    ps.setString(15, str(b, "guardian"));
                    ps.setString(16, str(b, "relation"));
                    ps.setDouble(17, b.get("weeklyPayment").getAsDouble());
                    ps.setString(18, photoPath);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    int empId = keys.next() ? keys.getInt(1) : -1;
                    c.commit();
                    res.status(201);
                    return gson.toJson(Map.of("employee_id", empId, "user_id", userId, "weekly_payment", b.get("weeklyPayment").getAsDouble(), "photo_path", photoPath));
                } catch (SQLException ex) {
                    c.rollback();
                    res.status(400);
                    return gson.toJson(Map.of("error", ex.getMessage()));
                } finally {
                    c.setAutoCommit(true);
                }
            }
        });

        get("/employees/:id", (req, res) -> {
            int id = Integer.parseInt(req.params(":id"));
            try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement("SELECT * FROM employees WHERE id=?")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) { res.status(404); return gson.toJson(Map.of("error","not_found")); }
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("first_name", rs.getString("first_name"));
                m.put("last_name", rs.getString("last_name"));
                m.put("weekly_payment", rs.getDouble("weekly_payment"));
                m.put("photo_path", rs.getString("photo_path"));
                return gson.toJson(m);
            }
        });

        get("/employees/:id/orders", (req, res) -> {
            int empId = Integer.parseInt(req.params(":id"));
            try (Connection c = Database.get(); PreparedStatement psEmp = c.prepareStatement("SELECT user_id FROM employees WHERE id=?")) {
                psEmp.setInt(1, empId);
                ResultSet rsEmp = psEmp.executeQuery();
                if (!rsEmp.next()) { res.status(404); return gson.toJson(Map.of("error","not_found")); }
                int userId = rsEmp.getInt("user_id");
                try (PreparedStatement ps = c.prepareStatement("SELECT id,total,created_at FROM orders WHERE cashier_id=? ORDER BY id DESC")) {
                    ps.setInt(1, userId);
                    ResultSet rs = ps.executeQuery();
                    List<Map<String,Object>> list = new ArrayList<>();
                    while (rs.next()) {
                        Map<String,Object> m = new LinkedHashMap<>();
                        m.put("id", rs.getInt("id"));
                        m.put("total", rs.getDouble("total"));
                        m.put("created_at", rs.getString("created_at"));
                        list.add(m);
                    }
                    return gson.toJson(list);
                }
            }
        });

        get("/employees/by-username/:username", (req, res) -> {
            String username = req.params(":username");
            try (Connection c = Database.get(); PreparedStatement ps = c.prepareStatement("SELECT e.*, u.username FROM employees e JOIN users u ON e.user_id=u.id WHERE u.username=?")) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("user_id", rs.getInt("user_id"));
                    m.put("first_name", rs.getString("first_name"));
                    m.put("middle_name", rs.getString("middle_name"));
                    m.put("last_name", rs.getString("last_name"));
                    m.put("weekly_payment", rs.getDouble("weekly_payment"));
                    m.put("photo_path", rs.getString("photo_path"));
                    m.put("username", username);
                    return gson.toJson(m);
                }
                res.status(404);
                return gson.toJson(Map.of("error","not_found"));
            }
        });

        get("/employees/:id/summary", (req, res) -> {
            int empId = Integer.parseInt(req.params(":id"));
            String month = Optional.ofNullable(req.queryParams("month")).orElse(LocalDate.now().toString().substring(0,7));
            try (Connection c = Database.get(); PreparedStatement psEmp = c.prepareStatement("SELECT user_id FROM employees WHERE id=?")) {
                psEmp.setInt(1, empId);
                ResultSet rsEmp = psEmp.executeQuery();
                if (!rsEmp.next()) { res.status(404); return gson.toJson(Map.of("error","not_found")); }
                int userId = rsEmp.getInt("user_id");
                try (PreparedStatement ps = c.prepareStatement("SELECT substr(created_at,1,10) as d, COUNT(*) as cnt, SUM(total) as revenue FROM orders WHERE cashier_id=? AND substr(created_at,1,7)=? GROUP BY d")) {
                    ps.setInt(1, userId);
                    ps.setString(2, month);
                    ResultSet rs = ps.executeQuery();
                    Map<String, Map<String,Object>> perDay = new HashMap<>();
                    int totalOrders = 0;
                    double totalRevenue = 0.0;
                    while (rs.next()) {
                        String d = rs.getString("d");
                        int cnt = rs.getInt("cnt");
                        double rev = rs.getDouble("revenue");
                        totalOrders += cnt;
                        totalRevenue += rev;
                        Map<String,Object> inf = new LinkedHashMap<>();
                        inf.put("date", d);
                        inf.put("orders", cnt);
                        inf.put("revenue", rev);
                        inf.put("present", true);
                        perDay.put(d, inf);
                    }
                    YearMonth ym = YearMonth.parse(month);
                    List<Map<String,Object>> days = new ArrayList<>();
                    for (int i=1; i<=ym.lengthOfMonth(); i++) {
                        String d = ym.atDay(i).toString();
                        if (perDay.containsKey(d)) {
                            days.add(perDay.get(d));
                        } else {
                            Map<String,Object> inf = new LinkedHashMap<>();
                            inf.put("date", d);
                            inf.put("orders", 0);
                            inf.put("revenue", 0.0);
                            inf.put("present", false);
                            days.add(inf);
                        }
                    }
                    long presentDays = days.stream().filter(x -> (Boolean)x.get("present")).count();
                    long absentDays = days.size() - presentDays;
                    Map<String,Object> out = new LinkedHashMap<>();
                    out.put("month", month);
                    out.put("total_orders", totalOrders);
                    out.put("total_revenue", totalRevenue);
                    out.put("present_days", presentDays);
                    out.put("absent_days", absentDays);
                    out.put("days", days);
                    return gson.toJson(out);
                }
            }
        });
    }

    private static Integer requireAuth(spark.Request req, spark.Response res) {
        String token = req.headers("Authorization");
        if (token == null) {
            res.status(401);
            return null;
        }
        Integer uid = sessions.get(token);
        if (uid == null) {
            res.status(401);
        }
        return uid;
    }

    private static String str(JsonObject b, String k) {
        return b.has(k) && !b.get(k).isJsonNull() ? b.get(k).getAsString() : null;
    }
}