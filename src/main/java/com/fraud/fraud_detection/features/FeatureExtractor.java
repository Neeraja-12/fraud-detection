package com.fraud.fraud_detection.features;

import java.util.List;

/**
 * Stateless feature extraction. The output vector is deliberately "raw"
 * (logs where useful, no standardisation) — the StandardScaler in the model
 * handles scaling so it can be fit on training data only.
 */
public final class FeatureExtractor {

    public static final List<String> FEATURE_NAMES = List.of(
            "log_amount",
            "amount_vs_customer_avg",
            "txns_last_1h",
            "txns_last_24h",
            "is_foreign",
            "is_card_present",
            "is_odd_hour",
            "distance_from_home_km",
            "log_implied_speed_kmph",
            "high_risk_mcc",
            "new_device",
            "log_account_age_days",
            "failed_attempts_24h",
            "cross_border_cnp");

    public static final int N_FEATURES = FEATURE_NAMES.size();

    private FeatureExtractor() {
    }

    public static double[] extract(TxnContext c) {
        double[] x = new double[N_FEATURES];
        x[0] = Math.log1p(c.txn.amount());
        x[1] = Math.min(c.amountVsAvg, 50.0);
        x[2] = c.txnsLast1h;
        x[3] = c.txnsLast24h;
        x[4] = c.foreign ? 1.0 : 0.0;
        x[5] = c.txn.cardPresent() ? 1.0 : 0.0;
        x[6] = c.oddHour ? 1.0 : 0.0;
        x[7] = c.distanceFromHomeKm;
        x[8] = Math.log1p(c.impliedSpeedKmph);
        x[9] = c.highRiskMcc ? 1.0 : 0.0;
        x[10] = c.newDevice ? 1.0 : 0.0;
        x[11] = Math.log1p(c.profile.accountAgeDays());
        x[12] = c.profile.failedAttempts24h();
        x[13] = (c.foreign && !c.txn.cardPresent()) ? 1.0 : 0.0;
        return x;
    }
}