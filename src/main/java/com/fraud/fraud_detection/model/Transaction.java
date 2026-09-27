package com.fraud.fraud_detection.model;

/**
 * An immutable payment transaction.
 * Matches the authorization / ISO 8583 / card-network message shape.
 */
public record Transaction(
        String txnId,
        String customerId,
        double amount,
        String merchantId,
        String merchantCategory,   // MCC code, e.g. "5411"
        long timestampMs,
        double latitude,
        double longitude,
        boolean cardPresent,
        String deviceId,
        String countryCode
) {}