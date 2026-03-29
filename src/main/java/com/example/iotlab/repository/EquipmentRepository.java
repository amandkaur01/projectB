package com.example.iotlab.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iotlab.model.Equipment;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    // Exact match — used by BorrowService
    Optional<Equipment> findByName(String name);

    // Case-insensitive match — used by EquipmentService to detect duplicates
    Optional<Equipment> findByNameIgnoreCase(String name);

    // Used for low stock alerts
    List<Equipment> findByAvailableQuantityLessThan(int quantity);
}
