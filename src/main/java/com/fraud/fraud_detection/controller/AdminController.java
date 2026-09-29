package com.fraud.fraud_detection.controller;

import com.fraud.fraud_detection.ml.TrainingPipeline;
import com.fraud.fraud_detection.model.AuditLog;
import com.fraud.fraud_detection.model.Decision;
import com.fraud.fraud_detection.persistence.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TrainingPipeline pipeline;
    private final AuditLogRepository auditRepo;

    public AdminController(TrainingPipeline pipeline, AuditLogRepository auditRepo) {
        this.pipeline = pipeline;
        this.auditRepo = auditRepo;
    }

    @PostMapping("/train")
    public Map<String, Object> train() {
        long t0 = System.currentTimeMillis();
        TrainingPipeline.TrainResult r = pipeline.run();
        long elapsed = System.currentTimeMillis() - t0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "trained");
        out.put("trainSize", r.trainSize());
        out.put("valSize", r.valSize());
        out.put("testSize", r.testSize());
        out.put("positives", r.positives());
        out.put("precision", r.precision());
        out.put("recall", r.recall());
        out.put("f1", r.f1());
        out.put("prAuc", r.prAucModel());
        out.put("mlWeight", r.mlWeight());
        out.put("threshold", r.threshold());
        out.put("elapsedMs", elapsed);
        return out;
    }

    @PostMapping("/model-info")
    public Map<String, Object> modelInfo() {
        TrainingPipeline.LoadedModel m = TrainingPipeline.load();
        Map<String, Object> out = new LinkedHashMap<>();
        if (m == null) {
            out.put("trained", false);
            out.put("message", "No ./data/trained-model.txt — using hardcoded weights");
        } else {
            out.put("trained", true);
            out.put("summary", m.summary());
        }
        return out;
    }

    // -------- Audit log endpoints --------

    @GetMapping("/audit")
    public Map<String, Object> audit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String decision) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<AuditLog> result;
        if (decision != null && !decision.isBlank()) {
            try {
                result = auditRepo.findByDecisionOrderByEventTimestampDesc(
                        Decision.valueOf(decision.toUpperCase()), pageable);
            } catch (IllegalArgumentException e) {
                return Map.of("error", "Invalid decision: " + decision);
            }
        } else {
            result = auditRepo.findAllByOrderByEventTimestampDesc(pageable);
        }

        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::toMap)
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("page", result.getNumber());
        out.put("size", result.getSize());
        out.put("totalItems", result.getTotalElements());
        out.put("totalPages", result.getTotalPages());
        return out;
    }

    @GetMapping("/audit/stats")
    public Map<String, Object> auditStats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", auditRepo.count());
        out.put("allowed", auditRepo.countByDecision(Decision.ALLOW));
        out.put("reviewed", auditRepo.countByDecision(Decision.REVIEW));
        out.put("blocked", auditRepo.countByDecision(Decision.BLOCK));
        return out;
    }

    private Map<String, Object> toMap(AuditLog a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("txnId", a.getTxnId());
        m.put("customerId", a.getCustomerId());
        m.put("amount", a.getAmount());
        m.put("countryCode", a.getCountryCode());
        m.put("merchantCategory", a.getMerchantCategory());
        m.put("decision", a.getDecision().name());
        m.put("riskPoints", a.getRiskPoints());
        m.put("mlProbability", a.getMlProbability());
        m.put("ruleScore", a.getRuleScore());
        m.put("rulesFired", a.getRulesFired());
        m.put("modelVersion", a.getModelVersion());
        m.put("timestamp", a.getEventTimestamp().toString());
        return m;
    }
}