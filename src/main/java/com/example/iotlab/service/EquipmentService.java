package com.example.iotlab.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.iotlab.model.Equipment;
import com.example.iotlab.model.RestockRecord;
import com.example.iotlab.repository.EquipmentRepository;
import com.example.iotlab.repository.RestockRepository;

@Service
public class EquipmentService {

    @Autowired
    private EquipmentRepository repository;

    @Autowired(required = false)  // won't crash if RestockRepository is missing
    private RestockRepository restockRepository;

    /**
     * Add or update equipment by name (case-insensitive upsert). If the
     * equipment already exists → adds quantity on top. If new → creates a fresh
     * row.
     */
    public Equipment addEquipment(Equipment incoming) {

        Optional<Equipment> existing = repository
                .findByNameIgnoreCase(incoming.getName().trim());

        if (existing.isPresent()) {
            // ── UPDATE existing row ───────────────────────────────────────
            Equipment eq = existing.get();
            int stockBefore = eq.getAvailableQuantity();
            int addedQty = incoming.getTotalQuantity();

            eq.setTotalQuantity(eq.getTotalQuantity() + addedQty);
            eq.setAvailableQuantity(eq.getAvailableQuantity() + addedQty);

            if (incoming.getLocation() != null && !incoming.getLocation().isBlank()) {
                eq.setLocation(incoming.getLocation());
            }
            if (incoming.getCategory() != null && !incoming.getCategory().isBlank()) {
                eq.setCategory(incoming.getCategory());
            }

            Equipment saved = repository.save(eq);

            // Log restock record only if repository is available
            if (restockRepository != null) {
                try {
                    RestockRecord record = new RestockRecord(
                            saved.getName(),
                            saved.getCategory(),
                            addedQty,
                            stockBefore,
                            saved.getAvailableQuantity(),
                            LocalDate.now(),
                            null, null, null
                    );
                    restockRepository.save(record);
                } catch (Exception e) {
                    // Don't fail the whole request if restock logging fails
                    System.err.println("[EquipmentService] Restock log failed: " + e.getMessage());
                }
            }

            return saved;

        } else {
            // ── INSERT new row ────────────────────────────────────────────
            incoming.setName(incoming.getName().trim());
            incoming.setAvailableQuantity(incoming.getTotalQuantity());
            return repository.save(incoming);
        }
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
        return repository.findAll().stream()
                .mapToInt(Equipment::getAvailableQuantity).sum();
    }

    public int getTotalBorrowed() {
        return repository.findAll().stream()
                .mapToInt(e -> e.getTotalQuantity() - e.getAvailableQuantity()).sum();
    }
}
