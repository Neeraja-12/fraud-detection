package com.fraud.fraud_detection.rules;

import com.fraud.fraud_detection.features.TxnContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic policy layer. Covers cold-start, regulatory rules, and known
 * fraud typologies — and gives analysts a human-readable reason code.
 */
public final class RuleEngine {

    public record RuleHit(String rule, double weight, String detail) {
    }

    public List<RuleHit> evaluate(TxnContext c) {
        List<RuleHit> hits = new ArrayList<>();

        if (c.txn.amount() > 5000) {
            hits.add(new RuleHit("HIGH_AMOUNT", 2.0,
                    "Amount %.2f exceeds 5000".formatted(c.txn.amount())));
        }
        if (c.amountVsAvg > 8) {
            hits.add(new RuleHit("AMOUNT_ANOMALY", 2.5,
                    "Amount is %.1fx the customer average".formatted(c.amountVsAvg)));
        }
        if (c.txnsLast1h >= 20) {
            hits.add(new RuleHit("VELOCITY_1H", 2.0,
                    "%d transactions in the last hour".formatted(c.txnsLast1h)));
        }
        if (c.txnsLast24h >= 50) {
            hits.add(new RuleHit("VELOCITY_24H", 1.2,
                    "%d transactions in the last 24h".formatted(c.txnsLast24h)));
        }
        if (c.foreign) {
            hits.add(new RuleHit("CROSS_BORDER", 1.5,
                    "Transaction in %s, home country is %s"
                            .formatted(c.txn.countryCode(), c.profile.homeCountry())));
        }
        if (c.foreign && !c.txn.cardPresent()) {
            hits.add(new RuleHit("CROSS_BORDER_CNP", 2.0,
                    "Card-not-present cross-border transaction"));
        }
        if (c.oddHour) {
            hits.add(new RuleHit("ODD_HOURS", 0.8,
                    "Transaction between 00:00 and 06:00 UTC"));
        }
        if (c.newDevice) {
            hits.add(new RuleHit("NEW_DEVICE", 1.5,
                    "Device %s has not been seen for this customer".formatted(c.txn.deviceId())));
        }
        if (c.highRiskMcc) {
            hits.add(new RuleHit("HIGH_RISK_MCC", 1.8,
                    "Merchant category %s is high risk".formatted(c.txn.merchantCategory())));
        }
        if (c.impliedSpeedKmph > 900) {
            hits.add(new RuleHit("IMPOSSIBLE_TRAVEL", 3.0,
                    "Implied travel speed %.0f km/h".formatted(c.impliedSpeedKmph)));
        }
        if (c.distanceFromHomeKm > 500) {
            hits.add(new RuleHit("FAR_FROM_HOME", 1.0,
                    "%.0f km from home location".formatted(c.distanceFromHomeKm)));
        }
        if (c.profile.accountAgeDays() < 30 && c.txn.amount() > 1000) {
            hits.add(new RuleHit("NEW_ACCOUNT_BIG_TXN", 1.2,
                    "Account is %d days old".formatted(c.profile.accountAgeDays())));
        }
        if (c.profile.failedAttempts24h() >= 3) {
            hits.add(new RuleHit("AUTH_FAILURES", 1.5,
                    "%d failed authentication attempts in 24h"
                            .formatted(c.profile.failedAttempts24h())));
        }
        return hits;
    }

    /**
     * Saturating combination so the score stays in [0,1] no matter how many rules
     * fire.
     */
    public double score(List<RuleHit> hits) {
        double sum = 0.0;
        for (RuleHit h : hits)
            sum += h.weight();
        return 1.0 - Math.exp(-sum / 4.0);
    }
}