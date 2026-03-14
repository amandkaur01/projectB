package com.example.iotlab.model;

import jakarta.persistence.*;

@Entity
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String category;
    private int totalQuantity;
    private int availableQuantity;
    private String location;

    public Equipment(){}

    public Long getId() { return id; }

    public String getName() { return name; }

    public String getCategory() { return category; }

    public int getTotalQuantity() { return totalQuantity; }

    public int getAvailableQuantity() { return availableQuantity; }

    public String getLocation() { return location; }

    public void setName(String name) { this.name = name; }

    public void setCategory(String category) { this.category = category; }

    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = totalQuantity; }

    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; }

    public void setLocation(String location) { this.location = location; }
}