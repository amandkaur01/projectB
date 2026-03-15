package com.example.iotlab.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Logged automatically when equipment available quantity drops to 0 (out of
 * stock) or below 30% of total (low stock). Used to calculate how long it took
 * the admin to restock.
 */
@Entity
public class StockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String equipmentName;
    private String alertType;      // "OUT_OF_STOCK" or "LOW_STOCK"
    private int stockAtAlert;   // available quantity when alert was triggered
    private LocalDate alertDate;
    private boolean resolved;       // true once restocked

    public StockAlert() {
    }

    public StockAlert(String equipmentName, String alertType,
            int stockAtAlert, LocalDate alertDate) {
        this.equipmentName = equipmentName;
        this.alertType = alertType;
        this.stockAtAlert = stockAtAlert;
        this.alertDate = alertDate;
        this.resolved = false;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────
    public Long getId() {
        return id;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public String getAlertType() {
        return alertType;
    }

    public int getStockAtAlert() {
        return stockAtAlert;
    }

    public LocalDate getAlertDate() {
        return alertDate;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setEquipmentName(String n) {
        this.equipmentName = n;
    }

    public void setAlertType(String t) {
        this.alertType = t;
    }

    public void setStockAtAlert(int s) {
        this.stockAtAlert = s;
    }

    public void setAlertDate(LocalDate d) {
        this.alertDate = d;
    }

    public void setResolved(boolean r) {
        this.resolved = r;
    }
}
