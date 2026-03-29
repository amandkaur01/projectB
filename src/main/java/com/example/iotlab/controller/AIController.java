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

    @Autowired(required = false)
    private AIService aiService;

    @Autowired(required = false)
    private EquipmentRepository equipmentRepository;

    @Autowired(required = false)
    private BorrowRepository borrowRepository;

    // ── Per-equipment stat holder ─────────────────────────────────────────
    private static class EquipmentStat {

        String name;
        long borrowCount;
        int availableStock;
        int totalStock;

        EquipmentStat(String n, long b, int a, int t) {
            name = n;
            borrowCount = b;
            availableStock = a;
            totalStock = t;
        }
    }

    private List<EquipmentStat> fetchRealStats() {
        if (equipmentRepository == null || borrowRepository == null) {
            return new ArrayList<>();
        }
        try {
            List<Equipment> items = equipmentRepository.findAll();
            List<EquipmentStat> stats = new ArrayList<>();
            for (Equipment e : items) {
                long count = borrowRepository.countByEquipmentName(e.getName());
                stats.add(new EquipmentStat(
                        e.getName(), count,
                        e.getAvailableQuantity(), e.getTotalQuantity()));
            }
            return stats;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String buildContextString(List<EquipmentStat> stats) {
        StringBuilder sb = new StringBuilder();
        for (EquipmentStat s : stats) {
            sb.append(s.name).append(" borrowed ").append(s.borrowCount)
                    .append(" times, available stock ").append(s.availableStock)
                    .append(" out of ").append(s.totalStock).append(". ");
        }
        return sb.toString().trim();
    }

    // ── 1. Usage Analysis ─────────────────────────────────────────────────
    @GetMapping("/usage-analysis")
    public ResponseEntity<String> getUsageAnalysis() {
        try {
            List<EquipmentStat> stats = fetchRealStats();
            if (stats.isEmpty()) {
                return ResponseEntity.ok("No equipment data found in the database yet.");
            }

            if (aiService != null) {
                try {
                    String prompt = "Analyze this lab equipment usage data. "
                            + "Identify the most used and least used equipment with a short clear summary: "
                            + buildContextString(stats);
                    return ResponseEntity.ok(aiService.askAI(prompt));
                } catch (Exception ignored) {
                }
            }
            return ResponseEntity.ok(buildUsageAnalysis(stats));
        } catch (Exception e) {
            return ResponseEntity.ok("Usage analysis temporarily unavailable.");
        }
    }

    // ── 2. Purchase Recommendation ────────────────────────────────────────
    @GetMapping("/recommendation")
    public ResponseEntity<String> getRecommendation() {
        try {
            List<EquipmentStat> stats = fetchRealStats();
            if (stats.isEmpty()) {
                return ResponseEntity.ok("No equipment data found in the database yet.");
            }

            if (aiService != null) {
                try {
                    String prompt = "Based on this lab equipment usage data, recommend which equipment "
                            + "should be purchased or restocked and which should not. Be specific: "
                            + buildContextString(stats);
                    return ResponseEntity.ok(aiService.askAI(prompt));
                } catch (Exception ignored) {
                }
            }
            return ResponseEntity.ok(buildPurchaseRecommendation(stats));
        } catch (Exception e) {
            return ResponseEntity.ok("Purchase recommendation temporarily unavailable.");
        }
    }

    // ── 3. AI Chat ────────────────────────────────────────────────────────
    @PostMapping("/chat")
    public ResponseEntity<String> chatWithAI(@RequestBody ChatRequest request) {
        try {
            if (request.getQuestion() == null || request.getQuestion().isBlank()) {
                return ResponseEntity.badRequest().body("Please provide a question.");
            }

            List<EquipmentStat> stats = fetchRealStats();
            String context = stats.isEmpty() ? "No equipment in database yet."
                    : buildContextString(stats);

            if (aiService != null) {
                try {
                    String prompt = "You are a lab equipment management assistant. "
                            + "Equipment data: " + context
                            + " Answer this question concisely: " + request.getQuestion();
                    return ResponseEntity.ok(aiService.askAI(prompt));
                } catch (Exception ignored) {
                }
            }
            return ResponseEntity.ok(buildChatResponse(request.getQuestion(), stats));
        } catch (Exception e) {
            return ResponseEntity.ok("AI assistant temporarily unavailable.");
        }
    }

    // =========================================================================
    // FALLBACK — all methods use real DB data
    // =========================================================================
    private String buildUsageAnalysis(List<EquipmentStat> stats) {
        stats.sort(Comparator.comparingLong((EquipmentStat s) -> s.borrowCount).reversed());
        StringBuilder sb = new StringBuilder("📊 Equipment Usage Analysis (from database):\n\n");
        for (EquipmentStat s : stats) {
            String level = s.borrowCount == 0 ? "NEVER USED"
                    : s.borrowCount < 10 ? "LOW usage"
                            : s.borrowCount < 50 ? "MODERATE usage" : "HIGH usage";
            sb.append("• ").append(s.name).append(" — borrowed ").append(s.borrowCount)
                    .append(" times, ").append(s.availableStock).append(" in stock. ")
                    .append(level).append(".\n");
        }
        if (!stats.isEmpty()) {
            sb.append("\n✅ Most used: ").append(stats.get(0).name)
                    .append(" (").append(stats.get(0).borrowCount).append(" borrows)");
            sb.append("\n⬇️ Least used: ").append(stats.get(stats.size() - 1).name)
                    .append(" (").append(stats.get(stats.size() - 1).borrowCount).append(" borrows)");
        }
        return sb.toString();
    }

    private String buildPurchaseRecommendation(List<EquipmentStat> stats) {
        stats.sort(Comparator.comparingLong((EquipmentStat s) -> s.borrowCount).reversed());
        StringBuilder buy = new StringBuilder(), hold = new StringBuilder(), ok = new StringBuilder();
        for (EquipmentStat s : stats) {
            if (s.borrowCount > 5 && s.availableStock < 5) {
                buy.append("   • ").append(s.name).append(" — ").append(s.borrowCount)
                        .append(" borrows, only ").append(s.availableStock).append(" in stock. URGENT.\n"); 
            }else if (s.borrowCount > 5) {
                hold.append("   • ").append(s.name).append(" — ").append(s.borrowCount)
                        .append(" borrows, ").append(s.availableStock).append(" in stock. Monitor.\n"); 
            }else {
                ok.append("   • ").append(s.name).append(" — only ").append(s.borrowCount)
                        .append(" borrows, ").append(s.availableStock).append(" in stock. No action needed.\n");
            }
        }
        StringBuilder sb = new StringBuilder("🛒 Purchase Recommendation (from database):\n\n");
        if (buy.length() > 0) {
            sb.append("🔴 Buy Immediately:\n").append(buy).append("\n");
        }
        if (hold.length() > 0) {
            sb.append("🟡 Monitor Stock:\n").append(hold).append("\n");
        }
        if (ok.length() > 0) {
            sb.append("🟢 No Action Needed:\n").append(ok);
        }
        return sb.toString();
    }

    private String buildChatResponse(String question, List<EquipmentStat> stats) {
        String q = question.toLowerCase();
        if (stats.isEmpty()) {
            return "No equipment data found in the database yet.";
        }

        stats.sort(Comparator.comparingLong((EquipmentStat s) -> s.borrowCount).reversed());

        if ((q.contains("restock") || q.contains("restocked") || q.contains("added"))
                && (q.contains("recent") || q.contains("latest") || q.contains("last")
                || q.contains("latestly") || q.contains("when") || q.contains("new"))) {
            return "📦 Check the Restock Log page for full restock history with dates.";
        }

        if (q.contains("most") || q.contains("popular") || q.contains("top")
                || q.contains("highest") || q.contains("maximum")) {
            EquipmentStat top = stats.get(0);
            return "📈 Most used: " + top.name + " with " + top.borrowCount
                    + " borrows (" + top.availableStock + " in stock).";
        }
        if (q.contains("least") || q.contains("rarely") || q.contains("unused")
                || q.contains("lowest") || q.contains("never")) {
            EquipmentStat bottom = stats.get(stats.size() - 1);
            return "📉 Least used: " + bottom.name + " with only " + bottom.borrowCount
                    + " borrows (" + bottom.availableStock + " in stock).";
        }
        if (q.contains("restock") || q.contains("buy") || q.contains("purchase") || q.contains("order")) {
            return buildPurchaseRecommendation(stats);
        }

        if (q.contains("out of stock") || q.contains("unavailable") || q.contains("empty")) {
            List<EquipmentStat> out = new ArrayList<>();
            for (EquipmentStat s : stats) {
                if (s.availableStock == 0) {
                    out.add(s);
                }
            }
            if (out.isEmpty()) {
                return "✅ All equipment currently has stock available.";
            }
            StringBuilder sb = new StringBuilder("❌ Out of stock:\n");
            for (EquipmentStat s : out) {
                sb.append("• ").append(s.name).append(" (").append(s.totalStock).append(" total)\n");
            }
            return sb.toString();
        }
        if (q.contains("available") || q.contains("stock") || q.contains("inventory")) {
            StringBuilder sb = new StringBuilder("📦 Current Stock:\n\n");
            for (EquipmentStat s : stats) {
                String status = s.availableStock == 0 ? "OUT OF STOCK ❌"
                        : s.availableStock < 5 ? "LOW STOCK ⚠️" : "Available ✅";
                sb.append("• ").append(s.name).append(": ").append(s.availableStock)
                        .append(" — ").append(status).append("\n");
            }
            return sb.toString();
        }
        if (q.contains("overdue")) {
            return "⏰ Check the Borrow Records page to see all overdue items. "
                    + "Overdue fine warning emails are sent automatically at 8 AM daily.";
        }

        // Generic overview
        StringBuilder sb = new StringBuilder("🤖 Lab Equipment Overview:\n\n");
        for (EquipmentStat s : stats) {
            sb.append("• ").append(s.name).append(": ").append(s.borrowCount)
                    .append(" borrows, ").append(s.availableStock).append(" in stock\n");
        }
        sb.append("\nTry: 'most used', 'restock recommendations', 'out of stock', 'available inventory'");
        return sb.toString();
    }
}
