package com.example.iotlab.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class RestockRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String equipmentName;
    private String category;

    private int quantityAdded;
    private int stockBefore;
    private int stockAfter;

    private LocalDate restockDate;

    // ── Alert linkage (null if no alert existed before restock) ──────────
    private LocalDate alertDate;    // when admin was first warned about low/zero stock
    private String alertType;    // "OUT_OF_STOCK" or "LOW_STOCK" or null
    private Integer daysTaken;    // restockDate - alertDate (null if no prior alert)

    public RestockRecord() {
    }

    public RestockRecord(String equipmentName, String category,
            int quantityAdded, int stockBefore, int stockAfter,
            LocalDate restockDate,
            LocalDate alertDate, String alertType, Integer daysTaken) {
        this.equipmentName = equipmentName;
        this.category = category;
        this.quantityAdded = quantityAdded;
        this.stockBefore = stockBefore;
        this.stockAfter = stockAfter;
        this.restockDate = restockDate;
        this.alertDate = alertDate;
        this.alertType = alertType;
        this.daysTaken = daysTaken;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────
    public Long getId() {
        return id;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public String getCategory() {
        return category;
    }

    public int getQuantityAdded() {
        return quantityAdded;
    }

    public int getStockBefore() {
        return stockBefore;
    }

    public int getStockAfter() {
        return stockAfter;
    }

    public LocalDate getRestockDate() {
        return restockDate;
    }

    public LocalDate getAlertDate() {
        return alertDate;
    }

    public String getAlertType() {
        return alertType;
    }

    public Integer getDaysTaken() {
        return daysTaken;
    }

    public void setEquipmentName(String n) {
        this.equipmentName = n;
    }

    public void setCategory(String c) {
        this.category = c;
    }

    public void setQuantityAdded(int q) {
        this.quantityAdded = q;
    }

    public void setStockBefore(int s) {
        this.stockBefore = s;
    }

    public void setStockAfter(int s) {
        this.stockAfter = s;
    }

    public void setRestockDate(LocalDate d) {
        this.restockDate = d;
    }

    public void setAlertDate(LocalDate d) {
        this.alertDate = d;
    }

    public void setAlertType(String t) {
        this.alertType = t;
    }

    public void setDaysTaken(Integer d) {
        this.daysTaken = d;
    }
}
