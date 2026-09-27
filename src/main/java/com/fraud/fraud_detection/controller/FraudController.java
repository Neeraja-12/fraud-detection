package com.fraud.fraud_detection.controller;

import com.fraud.fraud_detection.engine.FraudDetectionEngine;
import com.fraud.fraud_detection.model.CustomerProfile;
import com.fraud.fraud_detection.model.Transaction;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class FraudController {

    private final FraudDetectionEngine engine;

    public FraudController(FraudDetectionEngine engine) {
        this.engine = engine;
    }

    /** Register a customer so we have behavioral history for scoring. */
    @PostMapping("/customers")
    public Map<String, Object> registerCustomer(@RequestBody Map<String, Object> body) {
        String customerId = (String) body.get("customerId");
        double homeLat = ((Number) body.getOrDefault("homeLat", 0)).doubleValue();
        double homeLon = ((Number) body.getOrDefault("homeLon", 0)).doubleValue();
        String homeCountry = (String) body.getOrDefault("homeCountry", "US");
        int accountAgeDays = ((Number) body.getOrDefault("accountAgeDays", 365)).intValue();

        CustomerProfile profile = new CustomerProfile(
                customerId, homeLat, homeLon, homeCountry, accountAgeDays);

        Object deviceObj = body.get("knownDevice");
        if (deviceObj instanceof String device) {
            profile.addKnownDevice(device);
        }

        engine.register(profile);
        return Map.of("status", "registered", "customerId", customerId);
    }

    /** Score a transaction — the main fraud decision endpoint. */
    @PostMapping("/score")
    public FraudDetectionEngine.Assessment score(@RequestBody Transaction txn) {
        return engine.assess(txn);
    }

    /** Look up a customer profile (for debugging). */
    @GetMapping("/customers/{customerId}")
    public Map<String, Object> getProfile(@PathVariable String customerId) {
        CustomerProfile p = engine.getProfile(customerId);
        if (p == null) {
            return Map.of("found", false, "customerId", customerId);
        }
        return Map.of(
                "found", true,
                "customerId", p.customerId(),
                "homeCountry", p.homeCountry(),
                "accountAgeDays", p.accountAgeDays(),
                "meanAmount", p.meanAmount(),
                "totalTxns", p.totalTxns());
    }
}