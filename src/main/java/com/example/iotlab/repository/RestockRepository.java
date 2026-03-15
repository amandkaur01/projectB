package com.example.iotlab.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iotlab.model.RestockRecord;

public interface RestockRepository extends JpaRepository<RestockRecord, Long> {

    // All restocks for one equipment, newest first
    List<RestockRecord> findByEquipmentNameOrderByRestockDateDesc(String equipmentName);

    // All records newest first (for admin history page)
    List<RestockRecord> findAllByOrderByRestockDateDesc();

    // Most recently restocked item
    RestockRecord findTopByOrderByRestockDateDesc();
}
