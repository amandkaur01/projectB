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
import com.example.iotlab.model.RestockRecord;
import com.example.iotlab.repository.BorrowRepository;
import com.example.iotlab.repository.EquipmentRepository;
import com.example.iotlab.repository.RestockRepository;
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
    @Autowired
    private RestockRepository restockRepository;

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

    // ── Build live stats from DB ──────────────────────────────────────────
    private List<EquipmentStat> fetchRealStats() {
        List<Equipment> items = equipmentRepository.findAll();
        List<EquipmentStat> stats = new ArrayList<>();
        for (Equipment e : items) {
            long count = borrowRepository.countByEquipmentName(e.getName());
            stats.add(new EquipmentStat(
                    e.getName(), count,
                    e.getAvailableQuantity(), e.getTotalQuantity()));
        }
        return stats;
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
        List<EquipmentStat> stats = fetchRealStats();
        if (stats.isEmpty()) {
            return ResponseEntity.ok("No equipment data found in the database yet.");
        }

        String prompt = "Analyze this lab equipment usage data. "
                + "Identify the most used and least used equipment with a short clear summary: "
                + buildContextString(stats);
        try {
            return ResponseEntity.ok(aiService.askAI(prompt));
        } catch (Exception e) {
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

        String prompt = "Based on this lab equipment usage data, recommend which equipment "
                + "should be purchased or restocked and which should not. Be specific: "
                + buildContextString(stats);
        try {
            return ResponseEntity.ok(aiService.askAI(prompt));
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
        String context = stats.isEmpty() ? "No equipment in database yet."
                : buildContextString(stats);

        String prompt = "You are a lab equipment management assistant. "
                + "Equipment data: " + context
                + " Answer this question concisely: " + request.getQuestion();
        try {
            return ResponseEntity.ok(aiService.askAI(prompt));
        } catch (Exception e) {
            return ResponseEntity.ok(buildChatResponse(request.getQuestion(), stats));
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
                            : s.borrowCount < 50 ? "MODERATE usage"
                                    : "HIGH usage";
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
            if (s.borrowCount > 20 && s.availableStock < 5) {
                buy.append("   • ").append(s.name).append(" — ").append(s.borrowCount)
                        .append(" borrows, only ").append(s.availableStock).append(" in stock. URGENT.\n"); 
            }else if (s.borrowCount > 20) {
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

        // ── Recently / last restocked ─────────────────────────────────────
        if ((q.contains("restock") || q.contains("restocked") || q.contains("added"))
                && (q.contains("recent") || q.contains("latest") || q.contains("last")
                || q.contains("latestly") || q.contains("when") || q.contains("new"))) {
            try {
                List<RestockRecord> recent = restockRepository.findAllByOrderByRestockDateDesc();
                if (recent.isEmpty()) {
                    return "📦 No restock records found yet. Restock history is tracked when you add quantity to existing equipment.";
                }
                StringBuilder sb = new StringBuilder("📦 Recent Restock History:\n\n");
                int limit = Math.min(5, recent.size());
                for (int i = 0; i < limit; i++) {
                    RestockRecord r = recent.get(i);
                    sb.append("• ").append(r.getEquipmentName())
                            .append(" — +").append(r.getQuantityAdded()).append(" units added on ")
                            .append(r.getRestockDate())
                            .append(" (stock: ").append(r.getStockBefore())
                            .append(" → ").append(r.getStockAfter()).append(")\n");
                }
                return sb.toString();
            } catch (Exception ex) {
                return "Could not fetch restock records. Please check the database.";
            }
        }

        // ── Most used ─────────────────────────────────────────────────────
        if (q.contains("most") || q.contains("popular") || q.contains("top")
                || q.contains("highest") || q.contains("maximum")) {
            EquipmentStat top = stats.get(0);
            return "📈 Most used equipment: " + top.name
                    + " with " + top.borrowCount + " borrows"
                    + " (" + top.availableStock + " currently in stock).";
        }

        // ── Least used ────────────────────────────────────────────────────
        if (q.contains("least") || q.contains("rarely") || q.contains("unused")
                || q.contains("lowest") || q.contains("minimum") || q.contains("never")) {
            EquipmentStat bottom = stats.get(stats.size() - 1);
            return "📉 Least used equipment: " + bottom.name
                    + " with only " + bottom.borrowCount + " borrows"
                    + " (" + bottom.availableStock + " in stock).";
        }

        // ── Restock / buy / purchase ──────────────────────────────────────
        if (q.contains("restock") || q.contains("buy") || q.contains("purchase")
                || q.contains("order") || q.contains("procure")) {
            return buildPurchaseRecommendation(stats);
        }

        // ── Out of stock ──────────────────────────────────────────────────
        if (q.contains("out of stock") || q.contains("unavailable") || q.contains("empty")
                || q.contains("zero") || q.contains("no stock")) {
            List<EquipmentStat> outOfStock = new ArrayList<>();
            for (EquipmentStat s : stats) {
                if (s.availableStock == 0) {
                    outOfStock.add(s);
                }
            }
            if (outOfStock.isEmpty()) {
                return "✅ Great news! All equipment currently has stock available.";
            }
            StringBuilder sb = new StringBuilder("❌ Equipment currently out of stock:\n\n");
            for (EquipmentStat s : outOfStock) {
                sb.append("• ").append(s.name).append(" (total: ").append(s.totalStock).append(" units)\n");
            }
            return sb.toString();
        }

        // ── Available / stock / inventory ─────────────────────────────────
        if (q.contains("available") || q.contains("stock") || q.contains("inventory")
                || q.contains("how many") || q.contains("quantity")) {
            StringBuilder sb = new StringBuilder("📦 Current Stock (from database):\n\n");
            for (EquipmentStat s : stats) {
                String status = s.availableStock == 0 ? "OUT OF STOCK ❌"
                        : s.availableStock < 5 ? "LOW STOCK ⚠️"
                                : "Available ✅";
                sb.append("• ").append(s.name).append(": ")
                        .append(s.availableStock).append(" units — ").append(status).append("\n");
            }
            return sb.toString();
        }

        // ── Usage / analysis ──────────────────────────────────────────────
        if (q.contains("usage") || q.contains("analysis") || q.contains("used")
                || q.contains("borrow") || q.contains("demand")) {
            return buildUsageAnalysis(stats);
        }

        // ── Generic fallback overview ─────────────────────────────────────
        StringBuilder sb = new StringBuilder("🤖 Lab Equipment Overview (from database):\n\n");
        for (EquipmentStat s : stats) {
            sb.append("• ").append(s.name).append(": ").append(s.borrowCount)
                    .append(" borrows, ").append(s.availableStock).append(" in stock\n");
        }
        sb.append("\nYou can ask:\n")
                .append("  • 'Which equipment is most used?'\n")
                .append("  • 'What should we restock?'\n")
                .append("  • 'Which equipment got recently restocked?'\n")
                .append("  • 'Show out of stock items'\n")
                .append("  • 'Show current inventory'");
        return sb.toString();
    }
}
