package com.fraud.fraud_detection.engine;

import com.fraud.fraud_detection.model.CustomerProfile;
import com.fraud.fraud_detection.model.Decision;
import com.fraud.fraud_detection.model.Transaction;
import com.fraud.fraud_detection.persistence.AuditLogRepository;
import com.fraud.fraud_detection.persistence.CustomerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FraudDetectionEngineTest {

    private FraudDetectionEngine engine;
    private CustomerProfile storedProfile;

    @BeforeEach
    void setUp() {
        CustomerProfileRepository repo = mock(CustomerProfileRepository.class);
        AuditLogRepository auditRepo = mock(AuditLogRepository.class);

        storedProfile = new CustomerProfile("C1", 19.0760, 72.8777, "IN", 900);
        storedProfile.addKnownDevice("known-phone");

        when(repo.findById(anyString())).thenReturn(Optional.of(storedProfile));
        when(repo.save(any(CustomerProfile.class))).thenAnswer(i -> i.getArguments()[0]);
        when(auditRepo.save(any())).thenAnswer(i -> i.getArguments()[0]);

        engine = new FraudDetectionEngine(repo, auditRepo);
        engine.init();
    }

    @Test
    void normalTransactionGetsLowRisk() {
        Transaction t = new Transaction(
                "T1", "C1", 1500, "M1", "5411",
                1_700_000_000_000L, 19.0760, 72.8777,
                true, "known-phone", "IN");
        FraudDetectionEngine.Assessment a = engine.assess(t);
        assertTrue(a.riskPoints() < 40, "Expected low risk, got " + a.riskPoints());
    }

    @Test
    void highRiskTransactionBlocks() {
        Transaction t = new Transaction(
                "T2", "C1", 85000, "LONDON", "6051",
                1_700_000_000_000L, 51.5074, -0.1278,
                false, "unknown-device", "GB");
        FraudDetectionEngine.Assessment a = engine.assess(t);
        assertTrue(a.riskPoints() > 60, "Expected high risk, got " + a.riskPoints());
        assertEquals(Decision.BLOCK, a.decision());
    }

    @Test
    void blockedTransactionIsNotRecordedInProfile() {
        long initialTotal = storedProfile.totalTxns();
        Transaction t = new Transaction(
                "T3", "C1", 85000, "LONDON", "6051",
                1_700_000_000_000L, 51.5074, -0.1278,
                false, "unknown-device", "GB");
        engine.assess(t);
        assertEquals(initialTotal, storedProfile.totalTxns());
    }

    @Test
    void allowedTransactionIsRecordedInProfile() {
        long initialTotal = storedProfile.totalTxns();
        Transaction t = new Transaction(
                "T4", "C1", 1500, "M1", "5411",
                1_700_000_000_000L, 19.0760, 72.8777,
                true, "known-phone", "IN");
        engine.assess(t);
        assertEquals(initialTotal + 1, storedProfile.totalTxns());
    }
}