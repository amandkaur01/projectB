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

        equipment.setAvailableQuantity(available - borrow.getQuantity());
        equipmentRepository.save(equipment);

        borrow.setBorrowDate(LocalDate.now());
        borrow.setDueDate(LocalDate.now().plusDays(7));
        borrow.setStatus("BORROWED");
        borrow.setReturnedQuantity(0);

        Borrow saved = borrowRepository.save(borrow);

        logStockAlertIfNeeded(equipment, equipment.getAvailableQuantity());

        return saved;
    }

    // ── Admin view — auto-detect overdue ──────────────────────────────────
    public List<Borrow> getAllBorrowed() {

        List<Borrow> borrows = borrowRepository.findAll();

        for (Borrow b : borrows) {

            if (b.getQuantity() > 0
                    && b.getReturnedQuantity() >= b.getQuantity()) {

                if (!"RETURNED".equals(b.getStatus())) {
                    b.setStatus("RETURNED");
                    borrowRepository.save(b);
                }

                continue;
            }

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

        int totalBorrowed = borrow.getQuantity();
        int alreadyReturned = borrow.getReturnedQuantity();

        int remaining = totalBorrowed - alreadyReturned;

        // validation
        if (qty <= 0 || qty > remaining) {
            return null;
        }

        // update returned quantity
        borrow.setReturnedQuantity(alreadyReturned + qty);

        // update equipment stock safely
        Equipment equipment = equipmentRepository
                .findByName(borrow.getEquipmentName())
                .orElse(null);

        if (equipment != null) {

            int available = equipment.getAvailableQuantity();
            int total = equipment.getTotalQuantity();

            int newAvailable = available + qty;

            if (newAvailable > total) {
                newAvailable = total;
            }

            equipment.setAvailableQuantity(newAvailable);

            equipmentRepository.save(equipment);

            logStockAlertIfNeeded(equipment, newAvailable);
        }

        // update borrow status
        if (borrow.getReturnedQuantity() >= borrow.getQuantity()) {
            borrow.setStatus("RETURNED");
        } else {
            borrow.setStatus("BORROWED");
        }

        return borrowRepository.save(borrow);
    }

    // ── Student History ───────────────────────────────────────────────────
    public List<Borrow> getBorrowsByStudent(String studentName) {

        List<Borrow> records = borrowRepository.findByStudentName(studentName);

        for (Borrow b : records) {

            if (b.getQuantity() > 0
                    && b.getReturnedQuantity() >= b.getQuantity()) {

                if (!"RETURNED".equals(b.getStatus())) {
                    b.setStatus("RETURNED");
                    borrowRepository.save(b);
                }

                continue;
            }

            if (!"RETURNED".equals(b.getStatus()) && b.getDueDate() != null) {

                if (b.getDueDate().isBefore(LocalDate.now())) {

                    if (!"OVERDUE".equals(b.getStatus())) {

                        b.setStatus("OVERDUE");
                        borrowRepository.save(b);
                    }
                }
            }
        }

        return records;
    }

    // ── Student Analytics ─────────────────────────────────────────────────
    public Map<String, Long> getStudentAnalytics(String studentName) {

        Map<String, Long> stats = new HashMap<>();

        long borrowed = borrowRepository.countByStudentName(studentName);

        long returned = borrowRepository
                .countByStudentNameAndStatus(studentName, "RETURNED");

        stats.put("borrowed", borrowed);
        stats.put("returned", returned);
        stats.put("pending", borrowed - returned);

        return stats;
    }

    // ── Stock alert helper ────────────────────────────────────────────────
    private void logStockAlertIfNeeded(Equipment equipment, int newAvailable) {

        if (stockAlertRepository
                .existsByEquipmentNameAndResolvedFalse(equipment.getName())) {
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

            stockAlertRepository.save(
                    new StockAlert(
                            equipment.getName(),
                            alertType,
                            newAvailable,
                            LocalDate.now()));
        }
    }
}
