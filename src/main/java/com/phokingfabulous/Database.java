package com.phokingfabulous;

import java.sql.*;

public class Database {
    public static Connection get() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:phoking.db");
    }

    public static void init() {
        try (Connection c = get(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password_hash TEXT NOT NULL, " +
                    "role TEXT NOT NULL DEFAULT 'cashier')");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS menu_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "price REAL NOT NULL, " +
                    "stock INTEGER NOT NULL DEFAULT 0)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS orders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "cashier_id INTEGER, " +
                    "total REAL NOT NULL, " +
                    "created_at TEXT NOT NULL, " +
                    "FOREIGN KEY(cashier_id) REFERENCES users(id))");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS order_items (" +
                    "order_id INTEGER NOT NULL, " +
                    "menu_item_id INTEGER NOT NULL, " +
                    "quantity INTEGER NOT NULL, " +
                    "price REAL NOT NULL, " +
                    "FOREIGN KEY(order_id) REFERENCES orders(id), " +
                    "FOREIGN KEY(menu_item_id) REFERENCES menu_items(id))");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS employees (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL, " +
                    "first_name TEXT, middle_name TEXT, last_name TEXT, suffix TEXT, " +
                    "dob TEXT, sex TEXT, contact TEXT, address TEXT, province TEXT, city TEXT, barangay TEXT, " +
                    "father TEXT, mother TEXT, guardian TEXT, relation TEXT, " +
                    "weekly_payment REAL NOT NULL, photo_path TEXT, " +
                    "FOREIGN KEY(user_id) REFERENCES users(id))");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}