package com.example.iotlab.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.iotlab.model.Borrow;
import com.example.iotlab.model.Equipment;
import com.example.iotlab.model.StockAlert;
import com.example.iotlab.repository.BorrowRepository;
import com.example.iotlab.repository.EquipmentRepository;
import com.example.iotlab.repository.StockAlertRepository;

@Service
public class BorrowService {

    @Autowired
    private BorrowRepository borrowRepository;
    @Autowired
    private EquipmentRepository equipmentRepository;
    @Autowired
    private StockAlertRepository stockAlertRepository;

    // ── Borrow Equipment ──────────────────────────────────────────────────
    public Borrow borrowEquipment(Borrow borrow) {

        Equipment equipment = equipmentRepository
                .findByName(borrow.getEquipmentName()).orElse(null);

        if (equipment == null) {
            return null;
        }

        int available = equipment.getAvailableQuantity();
        if (available < borrow.getQuantity()) {
            return null;
        }

        int newAvailable = available - borrow.getQuantity();
        equipment.setAvailableQuantity(newAvailable);
        equipmentRepository.save(equipment);

        borrow.setBorrowDate(LocalDate.now());
        borrow.setDueDate(LocalDate.now().plusDays(7));
        borrow.setStatus("BORROWED");
        borrow.setReturnedQuantity(0);

        Borrow saved = borrowRepository.save(borrow);

        // ── Log stock alert if needed ─────────────────────────────────────
        logStockAlertIfNeeded(equipment, newAvailable);

        return saved;
    }

    // ── Admin view with OVERDUE detection ─────────────────────────────────
    public List<Borrow> getAllBorrowed() {
        List<Borrow> borrows = borrowRepository.findAll();
        for (Borrow b : borrows) {
            if (!"RETURNED".equals(b.getStatus()) && b.getDueDate() != null) {
                if (b.getDueDate().isBefore(LocalDate.now())) {
                    b.setStatus("OVERDUE");
                    borrowRepository.save(b);
                }
            }
        }
        return borrows;
    }

    // ── Partial / Full Return ─────────────────────────────────────────────
    public Borrow returnEquipment(Long id, int qty) {

        Borrow borrow = borrowRepository.findById(id).orElse(null);
        if (borrow == null) {
            return null;
        }

        int remaining = borrow.getQuantity() - borrow.getReturnedQuantity();
        if (qty <= 0 || qty > remaining) {
            return null;
        }

        borrow.setReturnedQuantity(borrow.getReturnedQuantity() + qty);

        Equipment equipment = equipmentRepository
                .findByName(borrow.getEquipmentName()).orElse(null);

        if (equipment != null) {
            int newAvailable = Math.min(
                    equipment.getAvailableQuantity() + qty,
                    equipment.getTotalQuantity());
            equipment.setAvailableQuantity(newAvailable);
            equipmentRepository.save(equipment);

            // Re-check if stock is still low after return
            // (if it recovered, alert stays open until admin actively restocks)
            logStockAlertIfNeeded(equipment, newAvailable);
        }

        if (borrow.getReturnedQuantity() == borrow.getQuantity()) {
            borrow.setStatus("RETURNED"); 
        }else {
            borrow.setStatus("PARTIAL");
        }

        return borrowRepository.save(borrow);
    }

    // ── Student History ───────────────────────────────────────────────────
    public List<Borrow> getBorrowsByStudent(String studentName) {
        return borrowRepository.findByStudentName(studentName);
    }

    // ── Student Analytics ─────────────────────────────────────────────────
    public Map<String, Long> getStudentAnalytics(String studentName) {
        Map<String, Long> stats = new HashMap<>();
        long borrowed = borrowRepository.countByStudentName(studentName);
        long returned = borrowRepository.countByStudentNameAndStatus(studentName, "RETURNED");
        stats.put("borrowed", borrowed);
        stats.put("returned", returned);
        stats.put("pending", borrowed - returned);
        return stats;
    }

    // ── Private helper ────────────────────────────────────────────────────
    /**
     * Creates a StockAlert if: - available == 0 → OUT_OF_STOCK - available <
     * 30% of total → LOW_STOCK Only logs once per equipment (no duplicates
     * while still unresolved).
     */
    private void logStockAlertIfNeeded(Equipment equipment, int newAvailable) {
        // Skip if an unresolved alert already exists for this equipment
        if (stockAlertRepository.existsByEquipmentNameAndResolvedFalse(
                equipment.getName())) {
            return;
        }

        String alertType = null;

        if (newAvailable == 0) {
            alertType = "OUT_OF_STOCK";
        } else if (equipment.getTotalQuantity() > 0
                && (double) newAvailable / equipment.getTotalQuantity() < 0.30) {
            alertType = "LOW_STOCK";
        }

        if (alertType != null) {
            StockAlert alert = new StockAlert(
                    equipment.getName(),
                    alertType,
                    newAvailable,
                    LocalDate.now()
            );
            stockAlertRepository.save(alert);
        }
    }
}
