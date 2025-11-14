package com.phokingfabulous;

public class CartItem {
    private final MenuItem item;
    private int quantity;

    public CartItem(MenuItem item) {
        this.item = item;
        this.quantity = 1;
    }

    public MenuItem getItem() {
        return item;
    }

    public int getQuantity() {
        return quantity;
    }

    public void inc() {
        quantity++;
    }

    public void dec() {
        if (quantity > 1) quantity--;
    }

    public double getLineTotal() {
        return item.getPrice() * quantity;
    }

    @Override
    public String toString() {
        return quantity + " x " + item.getName() + " — ₱" + String.format("%.2f", getLineTotal());
    }
}