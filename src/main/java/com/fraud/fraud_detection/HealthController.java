package com.fraud.fraud_detection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "fraud-detection",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/hello")
    public String hello() {
        return "Fraud Detection API is alive";
    }
}