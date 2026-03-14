package com.example.iotlab.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.iotlab.model.Equipment;
import com.example.iotlab.repository.EquipmentRepository;

@Service
public class EquipmentService {

    @Autowired
    private EquipmentRepository repository;

    public Equipment addEquipment(Equipment equipment) {

        equipment.setAvailableQuantity(
                equipment.getTotalQuantity()
        );

        return repository.save(equipment);
    }

    public List<Equipment> getAllEquipment() {
        return repository.findAll();
    }

    public List<Equipment> getLowStockItems() {
        return repository.findByAvailableQuantityLessThan(5);
    }

    public void deleteEquipment(Long id) {
        repository.deleteById(id);
    }

    public long getTotalEquipment() {
        return repository.count();
    }

    public int getTotalAvailable() {
        return repository.findAll()
                .stream()
                .mapToInt(Equipment::getAvailableQuantity)
                .sum();
    }

    public int getTotalBorrowed() {
        return repository.findAll()
                .stream()
                .mapToInt(e -> e.getTotalQuantity() - e.getAvailableQuantity())
                .sum();
    }
}
