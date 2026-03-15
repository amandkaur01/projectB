package com.example.iotlab.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.iotlab.model.Equipment;
import com.example.iotlab.model.RestockRecord;
import com.example.iotlab.model.StockAlert;
import com.example.iotlab.repository.EquipmentRepository;
import com.example.iotlab.repository.RestockRepository;
import com.example.iotlab.repository.StockAlertRepository;

@Service
public class EquipmentService {

    @Autowired
    private EquipmentRepository repository;
    @Autowired
    private RestockRepository restockRepository;
    @Autowired
    private StockAlertRepository stockAlertRepository;

    /**
     * Add or update equipment.
     *
     * If equipment with this name already exists → update quantities. - Logs a
     * RestockRecord with alert linkage (alertDate, alertType, daysTaken). -
     * Resolves any open StockAlert for this equipment.
     *
     * If equipment is new → insert fresh row. No restock log.
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

            // ── Resolve open StockAlert + compute daysTaken ───────────────
            LocalDate alertDate = null;
            String alertType = null;
            Integer daysTaken = null;

            Optional<StockAlert> openAlert = stockAlertRepository
                    .findFirstByEquipmentNameAndResolvedFalseOrderByAlertDateAsc(
                            saved.getName());

            if (openAlert.isPresent()) {
                StockAlert alert = openAlert.get();
                alertDate = alert.getAlertDate();
                alertType = alert.getAlertType();
                daysTaken = (int) ChronoUnit.DAYS.between(alertDate, LocalDate.now());

                // Mark alert as resolved
                alert.setResolved(true);
                stockAlertRepository.save(alert);
            }

            // ── Log RestockRecord ─────────────────────────────────────────
            RestockRecord record = new RestockRecord(
                    saved.getName(),
                    saved.getCategory(),
                    addedQty,
                    stockBefore,
                    saved.getAvailableQuantity(),
                    LocalDate.now(),
                    alertDate,
                    alertType,
                    daysTaken
            );
            restockRepository.save(record);

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
