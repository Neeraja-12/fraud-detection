package com.fraud.fraud_detection.rules;

import com.fraud.fraud_detection.features.TxnContext;
import com.fraud.fraud_detection.model.CustomerProfile;
import com.fraud.fraud_detection.model.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    private static final long HOUR = 3600_000L;

    private final RuleEngine engine = new RuleEngine();

    private CustomerProfile newProfile(String id, String homeCountry) {
        CustomerProfile p = new CustomerProfile(id, 19.0760, 72.8777, homeCountry, 900);
        p.addKnownDevice("known-phone");
        return p;
    }

    private Transaction tx(double amount, String country, boolean cardPresent,
                           String device, long t) {
        return new Transaction(
                "T1", "C1", amount, "M1", "5411", t,
                19.0760, 72.8777, cardPresent, device, country);
    }

    @Test
    void highAmountFiresWhenAboveThreshold() {
        CustomerProfile p = newProfile("C1", "IN");
        Transaction t = tx(6000, "IN", true, "known-phone", 1_700_000_000_000L);
        TxnContext ctx = TxnContext.of(t, p);
        List<RuleEngine.RuleHit> hits = engine.evaluate(ctx);
        assertTrue(hits.stream().anyMatch(h -> h.rule().equals("HIGH_AMOUNT")));
    }

    @Test
    void crossBorderFiresForDifferentCountry() {
        CustomerProfile p = newProfile("C1", "IN");
        Transaction t = tx(100, "GB", true, "known-phone", 1_700_000_000_000L);
        TxnContext ctx = TxnContext.of(t, p);
        List<RuleEngine.RuleHit> hits = engine.evaluate(ctx);
        assertTrue(hits.stream().anyMatch(h -> h.rule().equals("CROSS_BORDER")));
    }

    @Test
    void newDeviceFiresForUnseenDevice() {
        CustomerProfile p = newProfile("C1", "IN");
        Transaction t = tx(100, "IN", true, "unknown-device", 1_700_000_000_000L);
        TxnContext ctx = TxnContext.of(t, p);
        List<RuleEngine.RuleHit> hits = engine.evaluate(ctx);
        assertTrue(hits.stream().anyMatch(h -> h.rule().equals("NEW_DEVICE")));
    }

    @Test
    void noRulesFireForNormalTransaction() {
        CustomerProfile p = newProfile("C1", "IN");
        for (int i = 0; i < 3; i++) {
            p.record(tx(1500, "IN", true, "known-phone", 1_699_000_000_000L + i * HOUR));
        }
        Transaction t = tx(1500, "IN", true, "known-phone", 1_699_000_000_000L + 4 * HOUR);
        TxnContext ctx = TxnContext.of(t, p);
        List<RuleEngine.RuleHit> hits = engine.evaluate(ctx);
        assertTrue(hits.isEmpty(), "Expected no rules, got: " + hits);
    }

    @Test
    void scoreSaturatesAtOne() {
        double s = engine.score(List.of(
                new RuleEngine.RuleHit("A", 5.0, ""),
                new RuleEngine.RuleHit("B", 5.0, ""),
                new RuleEngine.RuleHit("C", 5.0, "")
        ));
        assertTrue(s > 0.95 && s <= 1.0);
    }
}
