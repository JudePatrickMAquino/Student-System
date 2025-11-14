package com.phokingfabulous;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.time.LocalDate;

public class App extends Application {
    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();
    private final ListView<CartItem> cartView = new ListView<>(cart);
    private final Label totalLabel = new Label("Total: ₱0.00");
    private final ApiClient api = new ApiClient("http://localhost:4567");
    private GridPane menuGrid;
    private String authStatus = "Not logged in";

    @Override
    public void start(Stage stage) {
        stage.setTitle("Pho King Fabulous — JavaFX");

        Label header = new Label("Pho King Fabulous");
        header.setFont(Font.font(24));
        header.setPadding(new Insets(10));
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #b22222;");

        VBox left = buildMenuPane();
        VBox right = buildCartPane();

        HBox posContent = new HBox(20, left, right);
        posContent.setPadding(new Insets(15));
        posContent.setAlignment(Pos.TOP_CENTER);

        TabPane tabs = new TabPane();
        Tab loginTab = new Tab("Login", buildLoginPane());
        Tab posTab = new Tab("POS", posContent);
        Tab inventoryTab = new Tab("Inventory", buildInventoryPane());
        Tab reportsTab = new Tab("Reports", buildReportsPane());
        Tab registerTab = new Tab("Register Employee", buildEmployeeRegisterPane());
        loginTab.setClosable(false);
        posTab.setClosable(false);
        inventoryTab.setClosable(false);
        reportsTab.setClosable(false);
        tabs.getTabs().addAll(loginTab, posTab, inventoryTab, reportsTab, registerTab);

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(tabs);
        root.setStyle("-fx-background-color: linear-gradient(to bottom,#fff7f3,#ffe9e1);");

        Scene scene = new Scene(root, 960, 600);
        stage.setScene(scene);
        stage.show();
    }

    private VBox buildLoginPane() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        Label status = new Label(authStatus);
        TextField user = new TextField();
        user.setPromptText("Username");
        PasswordField pass = new PasswordField();
        pass.setPromptText("Password");
        Button loginBtn = new Button("Login");
        loginBtn.setOnAction(a -> {
            try {
                boolean ok = api.login(user.getText(), pass.getText());
                if (ok) {
                    authStatus = "Logged in";
                    status.setText(authStatus);
                    alert("Login success", Alert.AlertType.INFORMATION);
                } else {
                    alert("Login failed", Alert.AlertType.ERROR);
                }
            } catch (Exception e) {
                alert("Network error", Alert.AlertType.ERROR);
            }
        });
        box.getChildren().addAll(new Label("Staff Login"), user, pass, loginBtn, status);
        return box;
    }

    private VBox buildInventoryPane() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        TableView<InventoryItem> table = new TableView<>();
        TableColumn<InventoryItem, Number> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().id));
        TableColumn<InventoryItem, String> cName = new TableColumn<>("Name");
        cName.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().name));
        TableColumn<InventoryItem, Number> cPrice = new TableColumn<>("Price");
        cPrice.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().price));
        TableColumn<InventoryItem, Number> cStock = new TableColumn<>("Stock");
        cStock.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().stock));
        table.getColumns().addAll(cId, cName, cPrice, cStock);

        HBox form = new HBox(10);
        TextField name = new TextField();
        name.setPromptText("Name");
        TextField price = new TextField();
        price.setPromptText("Price");
        TextField stock = new TextField();
        stock.setPromptText("Stock");
        Button addBtn = new Button("Add");
        Button updateBtn = new Button("Update");
        Button deleteBtn = new Button("Delete");
        Button refreshBtn = new Button("Refresh");
        form.getChildren().addAll(name, price, stock, addBtn, updateBtn, deleteBtn, refreshBtn);

        refreshBtn.setOnAction(a -> {
            try {
                List<InventoryItem> items = api.listItems();
                table.getItems().setAll(items);
                if (menuGrid != null) populateMenuGrid(menuGrid);
            } catch (Exception e) {
                alert("Load failed", Alert.AlertType.ERROR);
            }
        });

        addBtn.setOnAction(a -> {
            try {
                if (api.getToken() == null) { alert("Login required", Alert.AlertType.WARNING); return; }
                String n = name.getText();
                double p = Double.parseDouble(price.getText());
                int s = Integer.parseInt(stock.getText());
                Integer id = api.createItem(n, p, s);
                if (id != null) { refreshBtn.fire(); if (menuGrid != null) populateMenuGrid(menuGrid); alert("Created", Alert.AlertType.INFORMATION); }
            } catch (Exception e) { alert("Create failed", Alert.AlertType.ERROR); }
        });

        updateBtn.setOnAction(a -> {
            try {
                if (api.getToken() == null) { alert("Login required", Alert.AlertType.WARNING); return; }
                InventoryItem sel = table.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                String n = name.getText().isEmpty() ? sel.name : name.getText();
                double p = price.getText().isEmpty() ? sel.price : Double.parseDouble(price.getText());
                int s = stock.getText().isEmpty() ? sel.stock : Integer.parseInt(stock.getText());
                boolean ok = api.updateItem(sel.id, n, p, s);
                if (ok) { refreshBtn.fire(); if (menuGrid != null) populateMenuGrid(menuGrid); alert("Updated", Alert.AlertType.INFORMATION); }
            } catch (Exception e) { alert("Update failed", Alert.AlertType.ERROR); }
        });

        deleteBtn.setOnAction(a -> {
            try {
                if (api.getToken() == null) { alert("Login required", Alert.AlertType.WARNING); return; }
                InventoryItem sel = table.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                boolean ok = api.deleteItem(sel.id);
                if (ok) { refreshBtn.fire(); if (menuGrid != null) populateMenuGrid(menuGrid); alert("Deleted", Alert.AlertType.INFORMATION); }
            } catch (Exception e) { alert("Delete failed", Alert.AlertType.ERROR); }
        });

        box.getChildren().addAll(new Label("Inventory"), table, form);
        return box;
    }

    private VBox buildReportsPane() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        TextField dateField = new TextField(LocalDate.now().toString());
        TextField monthField = new TextField(LocalDate.now().toString().substring(0,7));
        Button dailyBtn = new Button("Fetch Daily");
        Button monthlyBtn = new Button("Fetch Monthly");
        Label dailyOut = new Label();
        Label monthlyOut = new Label();

        TableView<com.google.gson.JsonObject> ordersTable = new TableView<>();
        TableColumn<com.google.gson.JsonObject, Number> oId = new TableColumn<>("Order");
        oId.setCellValueFactory(d -> new javafx.beans.property.SimpleIntegerProperty(d.getValue().get("id").getAsInt()));
        TableColumn<com.google.gson.JsonObject, String> oCashier = new TableColumn<>("Cashier");
        oCashier.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().get("cashier").getAsString()));
        TableColumn<com.google.gson.JsonObject, String> oDate = new TableColumn<>("Date");
        oDate.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().get("created_at").getAsString()));
        TableColumn<com.google.gson.JsonObject, Number> oTotal = new TableColumn<>("Total");
        oTotal.setCellValueFactory(d -> new javafx.beans.property.SimpleDoubleProperty(d.getValue().get("total").getAsDouble()));
        ordersTable.getColumns().addAll(oId, oCashier, oDate, oTotal);

        TextField empUserField = new TextField();
        empUserField.setPromptText("Employee Username");
        Button empLookupBtn = new Button("Employee Summary");
        TableView<com.google.gson.JsonObject> empOrdersTable = new TableView<>();
        TableColumn<com.google.gson.JsonObject, Number> eId = new TableColumn<>("Order");
        eId.setCellValueFactory(d -> new javafx.beans.property.SimpleIntegerProperty(d.getValue().get("id").getAsInt()));
        TableColumn<com.google.gson.JsonObject, String> eDate = new TableColumn<>("Date");
        eDate.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().get("created_at").getAsString()));
        TableColumn<com.google.gson.JsonObject, Number> eTotal = new TableColumn<>("Total");
        eTotal.setCellValueFactory(d -> new javafx.beans.property.SimpleDoubleProperty(d.getValue().get("total").getAsDouble()));
        empOrdersTable.getColumns().addAll(eId, eDate, eTotal);
        dailyBtn.setOnAction(a -> {
            try {
                double v = api.getDaily(dateField.getText());
                dailyOut.setText("₱" + format(v));
                var list = api.listOrdersByDate(dateField.getText());
                ordersTable.getItems().setAll(list);
            } catch (Exception e) { alert("Fetch failed", Alert.AlertType.ERROR); }
        });
        monthlyBtn.setOnAction(a -> {
            try { double v = api.getMonthly(monthField.getText()); monthlyOut.setText("₱" + format(v)); }
            catch (Exception e) { alert("Fetch failed", Alert.AlertType.ERROR); }
        });
        Label empInfo = new Label();
        Label empPay = new Label();
        Label empAttendance = new Label();

        empLookupBtn.setOnAction(a -> {
            try {
                String uname = empUserField.getText();
                var emp = api.getEmployeeByUsername(uname);
                if (emp == null) { alert("Employee not found", Alert.AlertType.WARNING); return; }
                int empId = emp.get("id").getAsInt();
                empInfo.setText(emp.get("first_name").getAsString() + " " + emp.get("last_name").getAsString());
                empPay.setText("Weekly Payment: ₱" + format(emp.get("weekly_payment").getAsDouble()));
                var list = api.listOrdersByEmployee(empId);
                empOrdersTable.getItems().setAll(list);
                var summary = api.getEmployeeSummary(empId, monthField.getText());
                if (summary != null) {
                    int present = summary.get("present_days").getAsInt();
                    int absent = summary.get("absent_days").getAsInt();
                    empAttendance.setText("Present: " + present + "  Absent: " + absent);
                }
            } catch (Exception e) { alert("Fetch failed", Alert.AlertType.ERROR); }
        });

        box.getChildren().addAll(
                new Label("Reports"),
                new HBox(10, new Label("Date"), dateField, dailyBtn, dailyOut),
                ordersTable,
                new HBox(10, new Label("Month"), monthField, monthlyBtn, monthlyOut),
                new HBox(10, empUserField, empLookupBtn),
                new HBox(10, empInfo, empPay, empAttendance),
                empOrdersTable
        );
        return box;
    }

    private VBox buildEmployeeRegisterPane() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField first = new TextField();
        TextField middle = new TextField();
        TextField last = new TextField();
        ComboBox<String> suffix = new ComboBox<>(FXCollections.observableArrayList("N/A","Jr","Sr"));
        suffix.getSelectionModel().selectFirst();
        DatePicker dob = new DatePicker();
        ComboBox<String> sex = new ComboBox<>(FXCollections.observableArrayList("Male","Female","Other"));
        TextField contact = new TextField();
        TextField address = new TextField();
        TextField province = new TextField();
        TextField city = new TextField();
        TextField barangay = new TextField();
        TextField father = new TextField();
        TextField mother = new TextField();
        TextField guardian = new TextField();
        TextField relation = new TextField();
        TextField weekly = new TextField();
        TextField username = new TextField();
        PasswordField pass = new PasswordField();
        PasswordField repass = new PasswordField();
        ComboBox<String> role = new ComboBox<>(FXCollections.observableArrayList("cashier","admin"));
        role.getSelectionModel().selectFirst();

        grid.add(new Label("First Name"),0,0); grid.add(first,1,0);
        grid.add(new Label("Middle Name"),2,0); grid.add(middle,3,0);
        grid.add(new Label("Last Name"),4,0); grid.add(last,5,0);
        grid.add(new Label("Suffix"),0,1); grid.add(suffix,1,1);
        grid.add(new Label("Date of Birth"),2,1); grid.add(dob,3,1);
        grid.add(new Label("Sex"),4,1); grid.add(sex,5,1);
        grid.add(new Label("Contact"),0,2); grid.add(contact,1,2);
        grid.add(new Label("Address"),2,2); grid.add(address,3,2,3,1);
        grid.add(new Label("Province"),0,3); grid.add(province,1,3);
        grid.add(new Label("City"),2,3); grid.add(city,3,3);
        grid.add(new Label("Barangay"),4,3); grid.add(barangay,5,3);
        grid.add(new Label("Father"),0,4); grid.add(father,1,4);
        grid.add(new Label("Mother"),2,4); grid.add(mother,3,4);
        grid.add(new Label("Guardian"),4,4); grid.add(guardian,5,4);
        grid.add(new Label("Relation"),0,5); grid.add(relation,1,5);
        grid.add(new Label("Weekly Payment"),2,5); grid.add(weekly,3,5);
        grid.add(new Label("Username"),4,5); grid.add(username,5,5);
        grid.add(new Label("Password"),0,6); grid.add(pass,1,6);
        grid.add(new Label("Re-Password"),2,6); grid.add(repass,3,6);
        grid.add(new Label("Role"),4,6); grid.add(role,5,6);

        HBox imageRow = new HBox(10);
        Button chooseImg = new Button("Choose Image");
        ImageView preview = new ImageView();
        preview.setFitWidth(120); preview.setFitHeight(120); preview.setPreserveRatio(true);
        final byte[][] photoBytes = new byte[1][];
        chooseImg.setOnAction(a -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Images","*.png","*.jpg","*.jpeg"));
            java.io.File f = fc.showOpenDialog(box.getScene().getWindow());
            if (f != null) {
                try {
                    photoBytes[0] = java.nio.file.Files.readAllBytes(f.toPath());
                    preview.setImage(new javafx.scene.image.Image(f.toURI().toString()));
                } catch (Exception ignored) {}
            }
        });
        imageRow.getChildren().addAll(new Label("Employee ID Image"), chooseImg, preview);

        Label summary = new Label();
        Button submit = new Button("Register");
        submit.setOnAction(a -> {
            try {
                if (!pass.getText().equals(repass.getText())) { alert("Passwords do not match", Alert.AlertType.WARNING); return; }
                double weeklyPay = Double.parseDouble(weekly.getText());
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("username", username.getText());
                obj.addProperty("password", pass.getText());
                obj.addProperty("role", role.getValue());
                obj.addProperty("firstName", first.getText());
                obj.addProperty("middleName", middle.getText());
                obj.addProperty("lastName", last.getText());
                obj.addProperty("suffix", suffix.getValue());
                obj.addProperty("dob", dob.getValue() == null ? null : dob.getValue().toString());
                obj.addProperty("sex", sex.getValue());
                obj.addProperty("contact", contact.getText());
                obj.addProperty("address", address.getText());
                obj.addProperty("province", province.getText());
                obj.addProperty("city", city.getText());
                obj.addProperty("barangay", barangay.getText());
                obj.addProperty("father", father.getText());
                obj.addProperty("mother", mother.getText());
                obj.addProperty("guardian", guardian.getText());
                obj.addProperty("relation", relation.getText());
                obj.addProperty("weeklyPayment", weeklyPay);

                com.google.gson.JsonObject res = api.createEmployee(obj, photoBytes[0]);
                if (res != null) {
                    summary.setText("Employee #" + res.get("employee_id").getAsInt() + " registered. Weekly payment ₱" + format(weeklyPay));
                    alert("Registered", Alert.AlertType.INFORMATION);
                } else {
                    alert("Registration failed", Alert.AlertType.ERROR);
                }
            } catch (Exception e) {
                alert("Invalid input or network error", Alert.AlertType.ERROR);
            }
        });

        box.getChildren().addAll(new Label("Employee Registration"), grid, imageRow, submit, summary);
        return box;
    }

    private VBox buildMenuPane() {
        VBox menuPane = new VBox(10);
        menuPane.setPrefWidth(420);
        menuPane.setPadding(new Insets(10));
        menuPane.setStyle("-fx-background-color: #ffffff; -fx-border-color: #ffb3a1; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");

        Label title = new Label("Menu");
        title.setFont(Font.font(18));
        title.setStyle("-fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));
        this.menuGrid = grid;

        Button refresh = new Button("Refresh Menu");
        refresh.setOnAction(a -> populateMenuGrid(grid));
        populateMenuGrid(grid);

        menuPane.getChildren().addAll(title, refresh, grid);
        return menuPane;
    }

    private void populateMenuGrid(GridPane grid) {
        grid.getChildren().clear();
        try {
            java.util.List<InventoryItem> items = api.listItems();
            int row = 0;
            for (InventoryItem inv : items) {
                MenuItem m = new MenuItem(inv.id, inv.name, inv.price);
                Label name = new Label(m.getName());
                Label price = new Label("₱" + String.format("%.2f", m.getPrice()));
                Button addBtn = new Button("Add");
                addBtn.setOnAction(a -> addToCart(m));
                grid.add(name, 0, row);
                grid.add(price, 1, row);
                grid.add(addBtn, 2, row);
                row++;
            }
        } catch (Exception e) {
            Label err = new Label("Unable to load menu");
            grid.add(err, 0, 0);
        }
    }

    private VBox buildCartPane() {
        VBox cartPane = new VBox(10);
        cartPane.setPrefWidth(480);
        cartPane.setPadding(new Insets(10));
        cartPane.setStyle("-fx-background-color: #ffffff; -fx-border-color: #ffb3a1; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");

        Label title = new Label("Your Cart");
        title.setFont(Font.font(18));
        title.setStyle("-fx-font-weight: bold;");

        cartView.setPrefHeight(360);

        HBox lineActions = new HBox(10);
        Button inc = new Button("+1");
        Button dec = new Button("-1");
        Button remove = new Button("Remove");
        inc.setOnAction(a -> modifySelected("inc"));
        dec.setOnAction(a -> modifySelected("dec"));
        remove.setOnAction(a -> modifySelected("remove"));
        lineActions.getChildren().addAll(inc, dec, remove);

        totalLabel.setFont(Font.font(16));

        HBox checkoutBox = new HBox(10);
        TextField paymentField = new TextField();
        paymentField.setPromptText("Enter payment amount");
        Button checkoutBtn = new Button("Checkout");
        checkoutBtn.setOnAction(a -> checkout(paymentField));
        checkoutBox.getChildren().addAll(paymentField, checkoutBtn);
        checkoutBox.setAlignment(Pos.CENTER_LEFT);

        cartPane.getChildren().addAll(title, cartView, lineActions, totalLabel, checkoutBox);
        return cartPane;
    }

    private void addToCart(MenuItem item) {
        for (CartItem ci : cart) {
            if (ci.getItem().getName().equals(item.getName())) {
                ci.inc();
                cartView.refresh();
                updateTotal();
                return;
            }
        }
        cart.add(new CartItem(item));
        updateTotal();
    }

    private void modifySelected(String action) {
        CartItem selected = cartView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        switch (action) {
            case "inc" -> selected.inc();
            case "dec" -> selected.dec();
            case "remove" -> cart.remove(selected);
        }
        cartView.refresh();
        updateTotal();
    }

    private void checkout(TextField paymentField) {
        if (cart.isEmpty()) {
            alert("Cart is empty", Alert.AlertType.INFORMATION);
            return;
        }
        double total = cart.stream().mapToDouble(CartItem::getLineTotal).sum();
        try {
            double payment = Double.parseDouble(paymentField.getText().trim());
            if (payment >= total) {
                double change = payment - total;
                try {
                    com.google.gson.JsonObject res = api.createOrder(new java.util.ArrayList<>(cart), payment, api.getToken());
                    if (res != null) {
                        alert("Payment received: ₱" + format(payment) + "\nChange: ₱" + format(change), Alert.AlertType.INFORMATION);
                    } else {
                        alert("Payment recorded locally only", Alert.AlertType.WARNING);
                    }
                } catch (Exception ex) {
                    alert("Failed to record order", Alert.AlertType.WARNING);
                }
                cart.clear();
                paymentField.clear();
                updateTotal();
            } else {
                alert("Insufficient payment. You still owe ₱" + format(total - payment), Alert.AlertType.WARNING);
            }
        } catch (NumberFormatException e) {
            alert("Invalid input. Enter a number.", Alert.AlertType.ERROR);
        }
    }

    private void updateTotal() {
        double total = cart.stream().mapToDouble(CartItem::getLineTotal).sum();
        totalLabel.setText("Total: ₱" + format(total));
    }

    private String format(double v) {
        return String.format("%.2f", v);
    }

    private void alert(String msg, Alert.AlertType type) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    public static void main(String[] args) {
        BackendServer.start();
        launch();
    }
}