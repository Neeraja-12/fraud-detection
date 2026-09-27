package com.fraud.fraud_detection.features;

import com.fraud.fraud_detection.model.CustomerProfile;
import com.fraud.fraud_detection.model.Transaction;
import com.fraud.fraud_detection.util.Geo;

import java.util.Set;

/**
 * Everything derived about a transaction, computed exactly once and shared
 * by both the feature extractor and the rule engine.
 */
public final class TxnContext {

    private static final Set<String> HIGH_RISK_MCC = Set.of("7995", "6051", "5967", "4829", "5966");

    private static final long HOUR_MS = 3600_000L;
    private static final long DAY_MS = 24 * HOUR_MS;

    public final Transaction txn;
    public final CustomerProfile profile;
    public final double distanceFromHomeKm;
    public final double impliedSpeedKmph;
    public final int txnsLast1h;
    public final int txnsLast24h;
    public final boolean foreign;
    public final boolean newDevice;
    public final boolean oddHour;
    public final boolean highRiskMcc;
    public final double amountVsAvg;

    private TxnContext(Transaction txn, CustomerProfile profile,
            double distanceFromHomeKm, double impliedSpeedKmph,
            int txnsLast1h, int txnsLast24h,
            boolean foreign, boolean newDevice, boolean oddHour,
            boolean highRiskMcc, double amountVsAvg) {
        this.txn = txn;
        this.profile = profile;
        this.distanceFromHomeKm = distanceFromHomeKm;
        this.impliedSpeedKmph = impliedSpeedKmph;
        this.txnsLast1h = txnsLast1h;
        this.txnsLast24h = txnsLast24h;
        this.foreign = foreign;
        this.newDevice = newDevice;
        this.oddHour = oddHour;
        this.highRiskMcc = highRiskMcc;
        this.amountVsAvg = amountVsAvg;
    }

    public static TxnContext of(Transaction t, CustomerProfile p) {
        double distHome = Geo.haversine(p.homeLat(), p.homeLon(), t.latitude(), t.longitude());

        Transaction last = p.lastTxn();
        double distLast = last == null
                ? distHome
                : Geo.haversine(last.latitude(), last.longitude(), t.latitude(), t.longitude());

        double hours = last == null
                ? 24.0
                : Math.max(1.0 / 60.0, (t.timestampMs() - last.timestampMs()) / (double) HOUR_MS);

        int h1 = p.countWithin(HOUR_MS, t.timestampMs());
        int h24 = p.countWithin(DAY_MS, t.timestampMs());

        int hourUtc = (int) ((t.timestampMs() / HOUR_MS) % 24);

        double avg = p.meanAmount();
        double amountVsAvg = (p.totalTxns() == 0 || avg <= 0) ? 1.0 : t.amount() / avg;

        return new TxnContext(
                t, p,
                distHome,
                distLast / hours,
                h1, h24,
                !p.homeCountry().equals(t.countryCode()),
                !p.isKnownDevice(t.deviceId()),
                hourUtc >= 0 && hourUtc < 6,
                HIGH_RISK_MCC.contains(t.merchantCategory()),
                amountVsAvg);
    }
}
