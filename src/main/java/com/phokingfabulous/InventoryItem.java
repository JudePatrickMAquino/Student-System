package com.phokingfabulous;

public class InventoryItem {
    public int id;
    public String name;
    public double price;
    public int stock;

    public InventoryItem(int id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    @Override
    public String toString() {
        return id + ": " + name + " (₱" + String.format("%.2f", price) + ", stock " + stock + ")";
    }
}