package com.phokingfabulous;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ApiClient {
    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private String token;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public boolean login(String username, String password) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            JsonObject obj = gson.fromJson(res.body(), JsonObject.class);
            token = obj.get("token").getAsString();
            return true;
        }
        return false;
    }

    public List<InventoryItem> listItems() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/inventory/items")).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        List<InventoryItem> list = new ArrayList<>();
        if (res.statusCode() == 200) {
            JsonArray arr = gson.fromJson(res.body(), JsonArray.class);
            for (var el : arr) {
                JsonObject o = el.getAsJsonObject();
                list.add(new InventoryItem(
                        o.get("id").getAsInt(),
                        o.get("name").getAsString(),
                        o.get("price").getAsDouble(),
                        o.get("stock").getAsInt()
                ));
            }
        }
        return list;
    }

    public Integer createItem(String name, double price, int stock) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("price", price);
        body.addProperty("stock", stock);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/inventory/items"))
                .header("Content-Type", "application/json")
                .header("Authorization", token == null ? "" : token)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 201) {
            JsonObject obj = gson.fromJson(res.body(), JsonObject.class);
            return obj.get("id").getAsInt();
        }
        return null;
    }

    public boolean updateItem(int id, String name, double price, int stock) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("price", price);
        body.addProperty("stock", stock);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/inventory/items/" + id))
                .header("Content-Type", "application/json")
                .header("Authorization", token == null ? "" : token)
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200;
    }

    public boolean deleteItem(int id) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/inventory/items/" + id))
                .header("Authorization", token == null ? "" : token)
                .DELETE()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() == 200;
    }

    public double getDaily(String date) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/reports/sales/daily?date=" + date)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            JsonObject obj = gson.fromJson(res.body(), JsonObject.class);
            return obj.get("revenue").getAsDouble();
        }
        return 0.0;
    }

    public double getMonthly(String month) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/reports/sales/monthly?month=" + month)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            JsonObject obj = gson.fromJson(res.body(), JsonObject.class);
            return obj.get("revenue").getAsDouble();
        }
        return 0.0;
    }

    public JsonObject createEmployee(JsonObject emp, byte[] photo) throws Exception {
        if (photo != null) {
            String b64 = java.util.Base64.getEncoder().encodeToString(photo);
            emp.addProperty("photoBase64", b64);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/employees"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(emp), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 201) {
            return gson.fromJson(res.body(), JsonObject.class);
        }
        return null;
    }

    public JsonObject createOrder(List<CartItem> items, double payment, String token) throws Exception {
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        for (CartItem ci : items) {
            Integer id = ci.getItem().getId();
            if (id == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("quantity", ci.getQuantity());
            arr.add(o);
        }
        body.add("items", arr);
        body.addProperty("payment", payment);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/orders"))
                .header("Content-Type", "application/json")
                .header("Authorization", token == null ? "" : token)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 201) {
            return gson.fromJson(res.body(), JsonObject.class);
        }
        return null;
    }

    public List<JsonObject> listOrdersByDate(String date) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/reports/orders?date=" + date)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        List<JsonObject> list = new ArrayList<>();
        if (res.statusCode() == 200) {
            JsonArray arr = gson.fromJson(res.body(), JsonArray.class);
            for (var el : arr) list.add(el.getAsJsonObject());
        }
        return list;
    }

    public List<JsonObject> listOrdersByEmployee(int empId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/employees/" + empId + "/orders")).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        List<JsonObject> list = new ArrayList<>();
        if (res.statusCode() == 200) {
            JsonArray arr = gson.fromJson(res.body(), JsonArray.class);
            for (var el : arr) list.add(el.getAsJsonObject());
        }
        return list;
    }

    public JsonObject getEmployeeByUsername(String username) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/employees/by-username/" + username)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            return gson.fromJson(res.body(), JsonObject.class);
        }
        return null;
    }

    public JsonObject getEmployeeSummary(int empId, String month) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/employees/" + empId + "/summary?month=" + month)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            return gson.fromJson(res.body(), JsonObject.class);
        }
        return null;
    }
}