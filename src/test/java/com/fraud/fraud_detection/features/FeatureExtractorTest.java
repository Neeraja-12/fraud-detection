package com.fraud.fraud_detection.features;

import com.fraud.fraud_detection.model.CustomerProfile;
import com.fraud.fraud_detection.model.Transaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureExtractorTest {

    private static Transaction tx(double amount, String country, boolean cardPresent,
                                  String device, long t) {
        return new Transaction(
                "T1", "C1", amount, "M1", "5411", t,
                19.0760, 72.8777, cardPresent, device, country);
    }

    @Test
    void extractReturns14Features() {
        CustomerProfile p = new CustomerProfile("C1", 19.0760, 72.8777, "IN", 900);
        p.addKnownDevice("known-phone");
        Transaction t = tx(1500, "IN", true, "known-phone", 1_700_000_000_000L);
        TxnContext ctx = TxnContext.of(t, p);
        double[] features = FeatureExtractor.extract(ctx);
        assertEquals(14, features.length);
        assertEquals(14, FeatureExtractor.FEATURE_NAMES.size());
        assertEquals(14, FeatureExtractor.N_FEATURES);
    }

    @Test
    void isForeignFlagIsSetForDifferentCountry() {
        CustomerProfile p = new CustomerProfile("C1", 19.0760, 72.8777, "IN", 900);
        p.addKnownDevice("known-phone");
        Transaction t = tx(100, "GB", true, "known-phone", 1_700_000_000_000L);
        TxnContext ctx = TxnContext.of(t, p);
        double[] features = FeatureExtractor.extract(ctx);
        assertEquals(1.0, features[4]);
    }

    @Test
    void newDeviceFlagIsSetForUnknownDevice() {
        CustomerProfile p = new CustomerProfile("C1", 19.0760, 72.8777, "IN", 900);
        p.addKnownDevice("known-phone");
        Transaction t = tx(100, "IN", true, "unknown-device", 1_700_000_000_000L);
        TxnContext ctx = TxnContext.of(t, p);
        double[] features = FeatureExtractor.extract(ctx);
        assertEquals(1.0, features[10]);
    }

    @Test
    void logAmountIsMonotonic() {
        CustomerProfile p = new CustomerProfile("C1", 19.0760, 72.8777, "IN", 900);
        p.addKnownDevice("known-phone");
        double[] small = FeatureExtractor.extract(
                TxnContext.of(tx(100, "IN", true, "known-phone", 1_700_000_000_000L), p));
        double[] large = FeatureExtractor.extract(
                TxnContext.of(tx(10000, "IN", true, "known-phone", 1_700_000_000_000L), p));
        assertTrue(large[0] > small[0]);
    }
}
