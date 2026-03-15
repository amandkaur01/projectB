package com.example.iotlab.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.iotlab.model.RestockRecord;
import com.example.iotlab.repository.RestockRepository;

@RestController
@RequestMapping("/restock")
@CrossOrigin
public class RestockController {

    @Autowired
    private RestockRepository restockRepository;

    // All restock records, newest first
    @GetMapping
    public List<RestockRecord> getAllRestocks() {
        return restockRepository.findAllByOrderByRestockDateDesc();
    }

    // Restock history for one specific equipment
    @GetMapping("/{equipmentName}")
    public List<RestockRecord> getRestocksForEquipment(
            @PathVariable String equipmentName) {
        return restockRepository
                .findByEquipmentNameOrderByRestockDateDesc(equipmentName);
    }
}
