package com.example.iotlab.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iotlab.model.Borrow;

public interface BorrowRepository extends JpaRepository<Borrow, Long> {

    // ── Used by BorrowService ─────────────────────────────────────────────
    // Fetch all borrow records for a specific student
    List<Borrow> findByStudentName(String studentName);

    // Count total borrows by student (used in student dashboard stats)
    long countByStudentName(String studentName);

    // Count borrows by student filtered by status (e.g. "BORROWED", "OVERDUE")
    long countByStudentNameAndStatus(String studentName, String status);

    // ── Used by DashboardController ───────────────────────────────────────
    // Count all borrow records with a given status (e.g. "BORROWED", "OVERDUE")
    long countByStatus(String status);

    // ── Used by AIController ──────────────────────────────────────────────
    // Count how many times a specific equipment was ever borrowed
    long countByEquipmentName(String equipmentName);
}
