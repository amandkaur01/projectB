package com.example.iotlab.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iotlab.model.StockAlert;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    // Find the earliest unresolved alert for an equipment (to calculate days taken)
    Optional<StockAlert> findFirstByEquipmentNameAndResolvedFalseOrderByAlertDateAsc(
            String equipmentName);

    // All unresolved alerts (for dashboard use if needed later)
    List<StockAlert> findByResolvedFalseOrderByAlertDateAsc();

    // Check if an unresolved alert already exists (to avoid duplicate alerts)
    boolean existsByEquipmentNameAndResolvedFalse(String equipmentName);
}
