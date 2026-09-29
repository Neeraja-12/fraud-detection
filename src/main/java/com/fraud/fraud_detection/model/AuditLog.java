package com.fraud.fraud_detection.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Permanent record of every fraud decision. Required for regulatory
 * compliance (RBI mandates 7-year retention of financial decisions).
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_customer", columnList = "customer_id"),
        @Index(name = "idx_audit_timestamp", columnList = "event_timestamp"),
        @Index(name = "idx_audit_decision", columnList = "decision")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_id", nullable = false)
    private String txnId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    private double amount;
    private String merchantId;
    private String merchantCategory;
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    private int riskPoints;
    private double mlProbability;
    private double ruleScore;

    @Column(length = 2000)
    private String rulesFired;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    public AuditLog() {
    }

    public AuditLog(String txnId, String customerId, double amount,
            String merchantId, String merchantCategory, String countryCode,
            Decision decision, int riskPoints,
            double mlProbability, double ruleScore,
            String rulesFired, String modelVersion) {
        this.txnId = txnId;
        this.customerId = customerId;
        this.amount = amount;
        this.merchantId = merchantId;
        this.merchantCategory = merchantCategory;
        this.countryCode = countryCode;
        this.decision = decision;
        this.riskPoints = riskPoints;
        this.mlProbability = mlProbability;
        this.ruleScore = ruleScore;
        this.rulesFired = rulesFired;
        this.modelVersion = modelVersion;
        this.eventTimestamp = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTxnId() {
        return txnId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public double getAmount() {
        return amount;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public Decision getDecision() {
        return decision;
    }

    public int getRiskPoints() {
        return riskPoints;
    }

    public double getMlProbability() {
        return mlProbability;
    }

    public double getRuleScore() {
        return ruleScore;
    }

    public String getRulesFired() {
        return rulesFired;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }
}