package com.fraud.fraud_detection.model;

/**
 * Final decision produced by the fraud engine for a transaction.
 *
 * ALLOW  - transaction is safe, proceed normally
 * REVIEW - suspicious, send to human analyst queue
 * BLOCK  - high confidence fraud, decline the transaction
 */
public enum Decision {
    ALLOW,
    REVIEW,
    BLOCK
}