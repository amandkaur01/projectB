package com.example.iotlab.controller;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.iotlab.model.Borrow;
import com.example.iotlab.model.Equipment;
import com.example.iotlab.repository.BorrowRepository;
import com.example.iotlab.repository.EquipmentRepository;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    @Autowired
    private BorrowRepository borrowRepository;
    @Autowired
    private EquipmentRepository equipmentRepository;

    /**
     * GET /api/analytics Returns all data needed for the analytics dashboard in
     * one call: - summary stats - mostBorrowed (top 6 equipment by borrow
     * count) - leastBorrowed (bottom 6 with at least 1 borrow, or all equipment
     * sorted asc) - monthlyTrend (last 6 months: borrows + returns per month) -
     * categoryUsage (borrow count grouped by equipment category)
     */
    @GetMapping
    public Map<String, Object> getAnalytics() {

        List<Borrow> allBorrows = borrowRepository.findAll();
        List<Equipment> allEquipment = equipmentRepository.findAll();

        Map<String, Object> result = new HashMap<>();

        // ── 1. Summary stats ─────────────────────────────────────────────
        long totalBorrows = allBorrows.size();
        long totalReturned = allBorrows.stream()
                .filter(b -> "RETURNED".equals(b.getStatus())).count();
        long totalOverdue = allBorrows.stream()
                .filter(b -> "OVERDUE".equals(b.getStatus())).count();
        long activeStudents = allBorrows.stream()
                .map(Borrow::getStudentName)
                .distinct().count();

        Map<String, Long> summary = new HashMap<>();
        summary.put("totalBorrows", totalBorrows);
        summary.put("totalReturned", totalReturned);
        summary.put("totalOverdue", totalOverdue);
        summary.put("activeStudents", activeStudents);
        result.put("summary", summary);

        // ── 2. Borrow count per equipment ─────────────────────────────────
        Map<String, Long> borrowCountMap = new HashMap<>();
        for (Borrow b : allBorrows) {
            String name = b.getEquipmentName();
            borrowCountMap.put(name, borrowCountMap.getOrDefault(name, 0L) + 1);
        }

        // Sort descending for most-borrowed
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(borrowCountMap.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        // Most borrowed — top 6
        List<Map<String, Object>> mostBorrowed = new ArrayList<>();
        int mostLimit = Math.min(6, sorted.size());
        for (int i = 0; i < mostLimit; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", sorted.get(i).getKey());
            item.put("count", sorted.get(i).getValue());
            mostBorrowed.add(item);
        }
        result.put("mostBorrowed", mostBorrowed);

        // Least borrowed — bottom 6 (or all equipment with 0 borrows included)
        // Include equipment that has never been borrowed too
        Map<String, Long> allEquipmentCounts = new LinkedHashMap<>();
        for (Equipment e : allEquipment) {
            allEquipmentCounts.put(e.getName(),
                    borrowCountMap.getOrDefault(e.getName(), 0L));
        }
        List<Map.Entry<String, Long>> sortedAsc = new ArrayList<>(allEquipmentCounts.entrySet());
        sortedAsc.sort(Comparator.comparingLong(Map.Entry::getValue));

        List<Map<String, Object>> leastBorrowed = new ArrayList<>();
        int leastLimit = Math.min(6, sortedAsc.size());
        for (int i = 0; i < leastLimit; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", sortedAsc.get(i).getKey());
            item.put("count", sortedAsc.get(i).getValue());
            leastBorrowed.add(item);
        }
        result.put("leastBorrowed", leastBorrowed);

        // ── 3. Monthly trend — last 6 months ──────────────────────────────
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> monthlyTrend = new ArrayList<>();

        for (int i = 5; i >= 0; i--) {
            LocalDate monthDate = today.minusMonths(i);
            int year = monthDate.getYear();
            int month = monthDate.getMonthValue();
            String monthLabel = monthDate.getMonth()
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    + " " + year;

            long borrows = allBorrows.stream()
                    .filter(b -> b.getBorrowDate() != null
                    && b.getBorrowDate().getYear() == year
                    && b.getBorrowDate().getMonthValue() == month)
                    .count();

            // Count returns based on due date month as proxy
            // (returnedQuantity > 0 and status RETURNED/PARTIAL)
            long returns = allBorrows.stream()
                    .filter(b -> b.getBorrowDate() != null
                    && b.getBorrowDate().getYear() == year
                    && b.getBorrowDate().getMonthValue() == month
                    && ("RETURNED".equals(b.getStatus())
                    || "PARTIAL".equals(b.getStatus())))
                    .count();

            Map<String, Object> point = new HashMap<>();
            point.put("month", monthLabel);
            point.put("borrows", borrows);
            point.put("returns", returns);
            monthlyTrend.add(point);
        }
        result.put("monthlyTrend", monthlyTrend);

        // ── 4. Category usage ─────────────────────────────────────────────
        // Map equipment name → category
        Map<String, String> equipCategoryMap = new HashMap<>();
        for (Equipment e : allEquipment) {
            equipCategoryMap.put(e.getName(), e.getCategory());
        }

        Map<String, Long> categoryCount = new LinkedHashMap<>();
        for (Borrow b : allBorrows) {
            String cat = equipCategoryMap.getOrDefault(
                    b.getEquipmentName(), "Other");
            categoryCount.put(cat, categoryCount.getOrDefault(cat, 0L) + 1);
        }

        // Sort by count descending
        List<Map.Entry<String, Long>> catSorted = new ArrayList<>(categoryCount.entrySet());
        catSorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        List<Map<String, Object>> categoryUsage = new ArrayList<>();
        for (Map.Entry<String, Long> entry : catSorted) {
            Map<String, Object> item = new HashMap<>();
            item.put("category", entry.getKey());
            item.put("count", entry.getValue());
            categoryUsage.add(item);
        }
        result.put("categoryUsage", categoryUsage);

        return result;
    }
}
