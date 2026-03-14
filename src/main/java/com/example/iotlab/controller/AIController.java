package com.example.iotlab.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.iotlab.dto.ChatRequest;
import com.example.iotlab.model.Equipment;
import com.example.iotlab.repository.BorrowRepository;
import com.example.iotlab.repository.EquipmentRepository;
import com.example.iotlab.service.AIService;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AIController {

    @Autowired
    private AIService aiService;
    @Autowired
    private EquipmentRepository equipmentRepository;
    @Autowired
    private BorrowRepository borrowRepository;

    // ── Inner class to hold per-equipment stats ───────────────────────────
    private static class EquipmentStat {

        String name;
        long borrowCount;
        int availableStock;
        int totalStock;

        EquipmentStat(String name, long borrowCount, int availableStock, int totalStock) {
            this.name = name;
            this.borrowCount = borrowCount;
            this.availableStock = availableStock;
            this.totalStock = totalStock;
        }
    }

    // ── Fetch real stats from the database ───────────────────────────────
    private List<EquipmentStat> fetchRealStats() {
        List<Equipment> items = equipmentRepository.findAll();
        List<EquipmentStat> stats = new ArrayList<>();
        for (Equipment e : items) {
            long count = borrowRepository.countByEquipmentName(e.getName());
            stats.add(new EquipmentStat(
                    e.getName(),
                    count,
                    e.getAvailableQuantity(),
                    e.getTotalQuantity()
            ));
        }
        return stats;
    }

    // Build the plain-text context string that gets sent to the AI model
    private String buildContextString(List<EquipmentStat> stats) {
        StringBuilder sb = new StringBuilder();
        for (EquipmentStat s : stats) {
            sb.append(s.name)
                    .append(" borrowed ").append(s.borrowCount).append(" times, ")
                    .append("available stock ").append(s.availableStock)
                    .append(" out of ").append(s.totalStock).append(". ");
        }
        return sb.toString().trim();
    }

    // ── 1. Usage Analysis ─────────────────────────────────────────────────
    @GetMapping("/usage-analysis")
    public ResponseEntity<String> getUsageAnalysis() {
        List<EquipmentStat> stats = fetchRealStats();

        if (stats.isEmpty()) {
            return ResponseEntity.ok("No equipment data found in the database yet.");
        }

        String context = buildContextString(stats);
        String prompt = "Analyze this lab equipment usage data. "
                + "Identify the most used and least used equipment with a short clear summary: "
                + context;

        try {
            String aiResponse = aiService.askAI(prompt);
            return ResponseEntity.ok(aiResponse);
        } catch (Exception e) {
            // AI unavailable — generate analysis from REAL DB data
            return ResponseEntity.ok(buildUsageAnalysis(stats));
        }
    }

    // ── 2. Purchase Recommendation ────────────────────────────────────────
    @GetMapping("/recommendation")
    public ResponseEntity<String> getRecommendation() {
        List<EquipmentStat> stats = fetchRealStats();

        if (stats.isEmpty()) {
            return ResponseEntity.ok("No equipment data found in the database yet.");
        }

        String context = buildContextString(stats);
        String prompt = "Based on this lab equipment usage data, recommend which equipment "
                + "should be purchased or restocked and which should not. Be specific: "
                + context;

        try {
            String aiResponse = aiService.askAI(prompt);
            return ResponseEntity.ok(aiResponse);
        } catch (Exception e) {
            return ResponseEntity.ok(buildPurchaseRecommendation(stats));
        }
    }

    // ── 3. AI Chat ────────────────────────────────────────────────────────
    @PostMapping("/chat")
    public ResponseEntity<String> chatWithAI(@RequestBody ChatRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().body("Please provide a question.");
        }

        List<EquipmentStat> stats = fetchRealStats();
        String context = stats.isEmpty() ? "No equipment in database yet." : buildContextString(stats);

        String prompt = "You are a lab equipment management assistant. "
                + "Equipment data: " + context
                + " Answer this question concisely: " + request.getQuestion();

        try {
            String aiResponse = aiService.askAI(prompt);
            return ResponseEntity.ok(aiResponse);
        } catch (Exception e) {
            return ResponseEntity.ok(buildChatResponse(request.getQuestion(), stats));
        }
    }

    // =========================================================================
    // FALLBACK METHODS — all use REAL database stats, never hardcoded values
    // =========================================================================
    private String buildUsageAnalysis(List<EquipmentStat> stats) {
        // Sort by borrow count descending
        stats.sort(Comparator.comparingLong((EquipmentStat s) -> s.borrowCount).reversed());

        StringBuilder sb = new StringBuilder("📊 Equipment Usage Analysis (from database):\n\n");

        for (EquipmentStat s : stats) {
            String level;
            if (s.borrowCount == 0) {
                level = "NEVER USED"; 
            }else if (s.borrowCount < 10) {
                level = "LOW usage"; 
            }else if (s.borrowCount < 50) {
                level = "MODERATE usage"; 
            }else {
                level = "HIGH usage";
            }

            sb.append("• ").append(s.name)
                    .append(" — borrowed ").append(s.borrowCount).append(" times")
                    .append(", ").append(s.availableStock).append(" in stock")
                    .append(". ").append(level).append(".\n");
        }

        if (!stats.isEmpty()) {
            EquipmentStat most = stats.get(0);
            EquipmentStat least = stats.get(stats.size() - 1);
            sb.append("\n✅ Most used: ").append(most.name)
                    .append(" (").append(most.borrowCount).append(" borrows)");
            sb.append("\n⬇️ Least used: ").append(least.name)
                    .append(" (").append(least.borrowCount).append(" borrows)");
        }

        return sb.toString();
    }

    private String buildPurchaseRecommendation(List<EquipmentStat> stats) {
        stats.sort(Comparator.comparingLong((EquipmentStat s) -> s.borrowCount).reversed());

        StringBuilder buy = new StringBuilder();
        StringBuilder hold = new StringBuilder();
        StringBuilder noNeed = new StringBuilder();

        for (EquipmentStat s : stats) {
            // High demand + low stock → buy urgently
            if (s.borrowCount > 20 && s.availableStock < 5) {
                buy.append("   • ").append(s.name)
                        .append(" — ").append(s.borrowCount).append(" borrows, only ")
                        .append(s.availableStock).append(" in stock. URGENT.\n");
            } // High demand but ok stock → monitor
            else if (s.borrowCount > 20) {
                hold.append("   • ").append(s.name)
                        .append(" — ").append(s.borrowCount).append(" borrows, ")
                        .append(s.availableStock).append(" in stock. Monitor closely.\n");
            } // Low demand → no action needed
            else {
                noNeed.append("   • ").append(s.name)
                        .append(" — only ").append(s.borrowCount).append(" borrows, ")
                        .append(s.availableStock).append(" in stock. No action needed.\n");
            }
        }

        StringBuilder sb = new StringBuilder("🛒 Purchase Recommendation (from database):\n\n");

        if (buy.length() > 0) {
            sb.append("🔴 Buy Immediately:\n").append(buy).append("\n");
        }
        if (hold.length() > 0) {
            sb.append("🟡 Monitor Stock:\n").append(hold).append("\n");
        }
        if (noNeed.length() > 0) {
            sb.append("🟢 No Action Needed:\n").append(noNeed);
        }

        return sb.toString();
    }

    private String buildChatResponse(String question, List<EquipmentStat> stats) {
        String q = question.toLowerCase();

        if (stats.isEmpty()) {
            return "No equipment data found in the database yet. "
                    + "Please add equipment first from the Add Equipment page.";
        }

        stats.sort(Comparator.comparingLong((EquipmentStat s) -> s.borrowCount).reversed());

        // Most used
        if (q.contains("most") || q.contains("popular") || q.contains("top")) {
            EquipmentStat top = stats.get(0);
            return "📈 Most used equipment: " + top.name
                    + " with " + top.borrowCount + " borrows"
                    + " (" + top.availableStock + " currently in stock).";
        }

        // Least used
        if (q.contains("least") || q.contains("rarely") || q.contains("unused")) {
            EquipmentStat bottom = stats.get(stats.size() - 1);
            return "📉 Least used equipment: " + bottom.name
                    + " with only " + bottom.borrowCount + " borrows"
                    + " (" + bottom.availableStock + " in stock).";
        }

        // Restock
        if (q.contains("restock") || q.contains("buy") || q.contains("purchase") || q.contains("order")) {
            return buildPurchaseRecommendation(stats);
        }

        // Available
        if (q.contains("available") || q.contains("stock") || q.contains("inventory")) {
            StringBuilder sb = new StringBuilder("📦 Current Stock from database:\n\n");
            for (EquipmentStat s : stats) {
                String stockStatus = s.availableStock == 0 ? "OUT OF STOCK ❌"
                        : s.availableStock < 5 ? "LOW STOCK ⚠️"
                                : "Available ✅";
                sb.append("• ").append(s.name)
                        .append(": ").append(s.availableStock).append(" units — ")
                        .append(stockStatus).append("\n");
            }
            return sb.toString();
        }

        // Generic overview
        StringBuilder sb = new StringBuilder("🤖 Lab Equipment Overview (from database):\n\n");
        for (EquipmentStat s : stats) {
            sb.append("• ").append(s.name)
                    .append(": ").append(s.borrowCount).append(" borrows, ")
                    .append(s.availableStock).append(" in stock\n");
        }
        sb.append("\nTry asking: 'Which equipment is most used?' or "
                + "'What should we restock?' or 'Show available stock'");
        return sb.toString();
    }
}
