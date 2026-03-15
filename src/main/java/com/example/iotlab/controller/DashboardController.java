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

        // ── Total Units ───────────────────────────────────────────────────
        List<Equipment> allEquipment = equipmentRepository.findAll();
        long totalUnits = allEquipment.stream()
                .mapToLong(Equipment::getTotalQuantity).sum();
        stats.put("totalEquipment", totalUnits);

        // ── Available Units ───────────────────────────────────────────────
        long availableUnits = allEquipment.stream()
                .mapToLong(Equipment::getAvailableQuantity).sum();
        stats.put("available", availableUnits);

        // ── Borrowed & Overdue ────────────────────────────────────────────
        List<Borrow> allBorrows = borrowRepository.findAll();
        long borrowedUnits = 0;
        long overdueUnits = 0;

        for (Borrow b : allBorrows) {

            // Fix stuck records: if fully returned, ensure status is RETURNED
            if (b.getQuantity() > 0
                    && b.getReturnedQuantity() >= b.getQuantity()) {
                if (!"RETURNED".equals(b.getStatus())) {
                    b.setStatus("RETURNED");
                    borrowRepository.save(b);
                }
                continue; // fully returned — don't count as borrowed or overdue
            }

            // Skip fully returned records
            if ("RETURNED".equals(b.getStatus())) {
                continue;
            }

            // Auto-detect overdue for active records
            if (b.getDueDate() != null && b.getDueDate().isBefore(LocalDate.now())) {
                if (!"OVERDUE".equals(b.getStatus())) {
                    b.setStatus("OVERDUE");
                    borrowRepository.save(b);
                }
            }

            // Count outstanding units
            int outstanding = Math.max(0, b.getQuantity() - b.getReturnedQuantity());
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
