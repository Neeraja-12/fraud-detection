package com.fraud.fraud_detection.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rolling behavioural profile for a cardholder.
 *
 * Persisted to H2. The engine explicitly calls syncForPersist() before save
 * and syncAfterLoad() after load — no JPA lifecycle callbacks, which proved
 * unreliable in this setup.
 */
@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {

    private static final long DAY_MS = 24L * 3600 * 1000;

    @Id
    @Column(nullable = false)
    private String customerId;

    private double homeLat;
    private double homeLon;
    private String homeCountry;
    private int accountAgeDays;

    private double meanAmount;
    private long totalTxns;
    private int failedAttempts24h;

    @Column(length = 4000)
    private String knownDevicesCsv;

    @Lob
    @Column(length = 1000000)
    private String historyBlob;

    @Transient
    private Set<String> knownDevices = new HashSet<>();

    @Transient
    private List<Transaction> history = new ArrayList<>();

    public CustomerProfile() {
    }

    public CustomerProfile(String customerId,
            double homeLat,
            double homeLon,
            String homeCountry,
            int accountAgeDays) {
        this.customerId = customerId;
        this.homeLat = homeLat;
        this.homeLon = homeLon;
        this.homeCountry = homeCountry;
        this.accountAgeDays = accountAgeDays;
    }

    // ----------------------------------------------------------------
    // Explicit sync — engine calls these; no JPA callbacks
    // ----------------------------------------------------------------

    /** Serialize transient working state into the text columns before saving. */
    public void syncForPersist() {
        knownDevicesCsv = String.join(",", knownDevices);

        StringBuilder sb = new StringBuilder();
        for (Transaction t : history) {
            sb.append(t.timestampMs()).append('|')
                    .append(t.latitude()).append('|')
                    .append(t.longitude()).append('|')
                    .append(t.amount()).append('|')
                    .append(t.deviceId() == null ? "" : t.deviceId()).append('|')
                    .append(t.countryCode() == null ? "" : t.countryCode())
                    .append('\n');
        }
        historyBlob = sb.toString();
    }

    /** Parse the text columns back into transient working state after load. */
    public void syncAfterLoad() {
        knownDevices.clear();
        if (knownDevicesCsv != null && !knownDevicesCsv.isBlank()) {
            for (String d : knownDevicesCsv.split(",")) {
                if (!d.isBlank())
                    knownDevices.add(d.trim());
            }
        }

        history.clear();
        if (historyBlob != null && !historyBlob.isBlank()) {
            for (String line : historyBlob.split("\n")) {
                if (line.isBlank())
                    continue;
                String[] p = line.split("\\|", -1);
                if (p.length != 6)
                    continue;
                try {
                    history.add(new Transaction(
                            "H-" + p[0],
                            customerId,
                            Double.parseDouble(p[3]),
                            "M-HIST",
                            "0000",
                            Long.parseLong(p[0]),
                            Double.parseDouble(p[1]),
                            Double.parseDouble(p[2]),
                            true,
                            p[4].isEmpty() ? null : p[4],
                            p[5].isEmpty() ? null : p[5]));
                } catch (Exception e) {
                    // Skip malformed lines silently
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // Public API (unchanged)
    // ----------------------------------------------------------------

    public void addKnownDevice(String deviceId) {
        if (deviceId != null)
            knownDevices.add(deviceId);
    }

    public boolean isKnownDevice(String deviceId) {
        return deviceId != null && knownDevices.contains(deviceId);
    }

    public void record(Transaction t) {
        history.add(t);
        totalTxns++;
        meanAmount += (t.amount() - meanAmount) / totalTxns;
        prune(t.timestampMs());
    }

    public void registerFailedAttempt() {
        failedAttempts24h++;
    }

    private void prune(long now) {
        while (!history.isEmpty()
                && now - history.get(0).timestampMs() > DAY_MS) {
            history.remove(0);
        }
    }

    public int countWithin(long windowMs, long now) {
        prune(now);
        int c = 0;
        for (Transaction t : history) {
            if (now - t.timestampMs() <= windowMs)
                c++;
        }
        return c;
    }

    public Transaction lastTxn() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public String customerId() {
        return customerId;
    }

    public double homeLat() {
        return homeLat;
    }

    public double homeLon() {
        return homeLon;
    }

    public String homeCountry() {
        return homeCountry;
    }

    public int accountAgeDays() {
        return accountAgeDays;
    }

    public double meanAmount() {
        return meanAmount;
    }

    public long totalTxns() {
        return totalTxns;
    }

    public int failedAttempts24h() {
        return failedAttempts24h;
    }
}