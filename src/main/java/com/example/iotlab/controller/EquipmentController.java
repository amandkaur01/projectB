package com.example.iotlab.controller;

import com.example.iotlab.model.Equipment;
import com.example.iotlab.service.EquipmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
@RestController
@RequestMapping("/equipment")
@CrossOrigin
public class EquipmentController {

    @Autowired
    private EquipmentService service;

    @PostMapping
    public Equipment addEquipment(@RequestBody Equipment equipment){
        return service.addEquipment(equipment);
    }

    @GetMapping
    public List<Equipment> getAllEquipment(){
        return service.getAllEquipment();
    }
    @GetMapping("/low-stock")
public List<Equipment> getLowStock(){
    return service.getLowStockItems();
}
    @DeleteMapping("/{id}")
    public void deleteEquipment(@PathVariable Long id){
        service.deleteEquipment(id);
    }
    @GetMapping("/dashboard")
public Map<String,Object> getDashboard(){

    Map<String,Object> data = new HashMap<>();

    data.put("totalEquipment", service.getTotalEquipment());
    data.put("availableEquipment", service.getTotalAvailable());
    data.put("borrowedEquipment", service.getTotalBorrowed());
    data.put("lowStockItems", service.getLowStockItems());

    return data;
}
}