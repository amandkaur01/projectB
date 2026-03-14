package com.example.iotlab.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.iotlab.model.Borrow;
import com.example.iotlab.model.Equipment;
import com.example.iotlab.repository.BorrowRepository;
import com.example.iotlab.repository.EquipmentRepository;

@Service
public class BorrowService {

    @Autowired
    private BorrowRepository borrowRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    // Borrow Equipment
    public Borrow borrowEquipment(Borrow borrow) {

        Equipment equipment = equipmentRepository
                .findByName(borrow.getEquipmentName())
                .orElse(null);

        if (equipment == null) {
            return null;
        }

        int available = equipment.getAvailableQuantity();

        if (available < borrow.getQuantity()) {
            return null;
        }

        // reduce available quantity
        equipment.setAvailableQuantity(available - borrow.getQuantity());
        equipmentRepository.save(equipment);

        borrow.setBorrowDate(LocalDate.now());
        borrow.setDueDate(LocalDate.now().plusDays(7)); // due in 7 days
        borrow.setStatus("BORROWED");
        borrow.setReturnedQuantity(0);

        return borrowRepository.save(borrow);
    }

    // Admin view with OVERDUE detection
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

    // Partial Return
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
                .findByName(borrow.getEquipmentName())
                .orElse(null);

        if (equipment != null) {

            int newAvailable = equipment.getAvailableQuantity() + qty;

            // prevent available > total
            if (newAvailable > equipment.getTotalQuantity()) {
                newAvailable = equipment.getTotalQuantity();
            }

            equipment.setAvailableQuantity(newAvailable);
            equipmentRepository.save(equipment);
        }

        // update status
        if (borrow.getReturnedQuantity() == borrow.getQuantity()) {
            borrow.setStatus("RETURNED");
        } else {
            borrow.setStatus("PARTIAL");
        }

        return borrowRepository.save(borrow);
    }

    // Student Borrow History
    public List<Borrow> getBorrowsByStudent(String studentName) {
        return borrowRepository.findByStudentName(studentName);
    }

    // Student Analytics
    public Map<String, Long> getStudentAnalytics(String studentName) {

        Map<String, Long> stats = new HashMap<>();

        long borrowed = borrowRepository.countByStudentName(studentName);
        long returned = borrowRepository.countByStudentNameAndStatus(studentName, "RETURNED");

        stats.put("borrowed", borrowed);
        stats.put("returned", returned);
        stats.put("pending", borrowed - returned);

        return stats;
    }
}
