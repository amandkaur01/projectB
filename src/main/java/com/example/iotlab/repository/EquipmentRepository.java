package com.example.iotlab.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.iotlab.model.Equipment;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    // Used when borrowing equipment
    Optional<Equipment> findByName(String name);

    // Used to detect duplicate equipment while adding/restocking
    Optional<Equipment> findByNameIgnoreCase(String name);

    // Used for low stock alerts
    List<Equipment> findByAvailableQuantityLessThan(int quantity);
}
