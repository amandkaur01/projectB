package com.example.iotlab.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.iotlab.model.Borrow;
import com.example.iotlab.model.Equipment;
import com.example.iotlab.repository.BorrowRepository;
import com.example.iotlab.repository.EquipmentRepository;

@RestController
@RequestMapping("/dashboard")
@CrossOrigin
public class DashboardController {

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private BorrowRepository borrowRepository;

    @GetMapping
    public Map<String, Long> getStats() {

        Map<String, Long> stats = new HashMap<>();

        // ── 1. Total Equipment ────────────────────────────────────────────
        // Sum of totalQuantity across all equipment rows (total physical units)
        List<Equipment> allEquipment = equipmentRepository.findAll();
        long totalUnits = allEquipment.stream()
                .mapToLong(Equipment::getTotalQuantity)
                .sum();
        stats.put("totalEquipment", totalUnits);

        // ── 2. Available Now ──────────────────────────────────────────────
        // Sum of availableQuantity across all equipment (units currently on shelf)
        long availableUnits = allEquipment.stream()
                .mapToLong(Equipment::getAvailableQuantity)
                .sum();
        stats.put("available", availableUnits);

        // ── 3. Currently Borrowed & 4. Overdue ───────────────────────────
        // Walk every active borrow record.
        // Auto-mark overdue if dueDate has passed and not yet returned.
        // Count borrowed units = sum of (quantity - returnedQuantity) for active records.
        List<Borrow> allBorrows = borrowRepository.findAll();

        long borrowedUnits = 0;
        long overdueUnits = 0;

        for (Borrow b : allBorrows) {

            // Skip fully returned records
            if ("RETURNED".equals(b.getStatus())) {
                continue;
            }

            // Auto-detect and persist overdue status
            if (b.getDueDate() != null && b.getDueDate().isBefore(LocalDate.now())) {
                if (!"OVERDUE".equals(b.getStatus())) {
                    b.setStatus("OVERDUE");
                    borrowRepository.save(b);
                }
            }

            // Units still outstanding on this record
            int outstanding = b.getQuantity() - b.getReturnedQuantity();
            if (outstanding < 0) {
                outstanding = 0;
            }

            borrowedUnits += outstanding;

            if ("OVERDUE".equals(b.getStatus())) {
                overdueUnits += outstanding;
            }
        }

        stats.put("borrowed", borrowedUnits);
        stats.put("overdue", overdueUnits);

        return stats;
    }
}
