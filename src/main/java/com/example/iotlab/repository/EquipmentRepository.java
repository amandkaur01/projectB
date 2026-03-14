package com.example.iotlab.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iotlab.model.Equipment;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByName(String name);

    List<Equipment> findByAvailableQuantityLessThan(int quantity);

}
