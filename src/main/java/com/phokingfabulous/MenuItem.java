package com.phokingfabulous;

public class MenuItem {
    private final Integer id;
    private final String name;
    private final double price;

    public MenuItem(Integer id, String name, double price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return name + " - ₱" + String.format("%.2f", price);
    }
}