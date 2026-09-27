package com.fraud.fraud_detection.ml;

import com.fraud.fraud_detection.features.FeatureExtractor;
import com.fraud.fraud_detection.features.TxnContext;
import com.fraud.fraud_detection.model.CustomerProfile;
import com.fraud.fraud_detection.model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulates a card portfolio with realistic (noisy, overlapping) fraud.
 * Returns a Dataset with extracted features and labels.
 *
 * Replace with your real historical data in production — the training
 * pipeline is unchanged.
 */
public final class SyntheticDataGenerator {

    private record City(String name, String country, double lat, double lon) {
    }

    private record FraudGeo(String country, double lat, double lon) {
    }

    private static final City[] CITIES = {
            new City("New York", "US", 40.7128, -74.0060),
            new City("London", "GB", 51.5074, -0.1278),
            new City("Mumbai", "IN", 19.0760, 72.8777),
            new City("Singapore", "SG", 1.3521, 103.8198),
            new City("Sao Paulo", "BR", -23.5505, -46.6333),
            new City("Sydney", "AU", -33.8688, 151.2093),
            new City("Toronto", "CA", 43.6532, -79.3832),
            new City("Dubai", "AE", 25.2048, 55.2708)
    };

    private static final FraudGeo[] FRAUD_GEOS = {
            new FraudGeo("RU", 55.7558, 37.6173),
            new FraudGeo("NG", 6.5244, 3.3792),
            new FraudGeo("CN", 31.2304, 121.4737),
            new FraudGeo("UA", 50.4501, 30.5234),
            new FraudGeo("VN", 10.8231, 106.6297)
    };

    private static final String[] LOW_RISK_MCC = {
            "5411", "5812", "5912", "5541", "4111", "5651", "7011", "5310", "5691"
    };

    private static final String[] HIGH_RISK_MCC = {
            "7995", "6051", "5967", "4829", "5966"
    };

    private SyntheticDataGenerator() {
    }

    public static Dataset generate(int nCustomers, int txnsPerCustomer,
            double fraudRate, long seed) {
        Random rnd = new Random(seed);

        List<double[]> feats = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();

        long now = System.currentTimeMillis();
        long windowMs = 90L * 24 * 3600 * 1000; // 90-day window

        for (int c = 0; c < nCustomers; c++) {
            City home = CITIES[rnd.nextInt(CITIES.length)];
            String customerId = "CUST-" + c;

            CustomerProfile profile = new CustomerProfile(
                    customerId, home.lat(), home.lon(), home.country(),
                    30 + rnd.nextInt(2200));

            String homeDevice = "DEV-" + rnd.nextInt(1_000_000);
            profile.addKnownDevice(homeDevice);

            double avgAmount = 500 + rnd.nextDouble() * 4500; // INR-scale
            long t = now - windowMs + (long) (rnd.nextDouble() * windowMs * 0.4);

            int made = 0;
            int seq = 0;

            while (made < txnsPerCustomer) {
                t += (long) (Math.exp(rnd.nextGaussian() * 0.9) * 5 * 3600_000L);

                if (rnd.nextDouble() >= fraudRate) {
                    // ---- legitimate activity -------------------------------------
                    if (hourUtc(t) < 6 && rnd.nextDouble() < 0.7)
                        t += 6 * 3600_000L;

                    Transaction tx = legit(rnd, customerId, seq++, home, homeDevice, avgAmount, t);
                    capture(feats, labels, tx, profile, 0);
                    profile.record(tx);
                    made++;
                } else {
                    // ---- fraud campaign: 1-3 rapid transactions -------------------
                    int burst = 1 + rnd.nextInt(3);
                    FraudGeo geo = FRAUD_GEOS[rnd.nextInt(FRAUD_GEOS.length)];
                    boolean goAbroad = rnd.nextDouble() < 0.75;
                    String fraudDevice = rnd.nextDouble() < 0.7
                            ? "DEV-X" + rnd.nextInt(100_000)
                            : homeDevice;
                    String mcc = rnd.nextDouble() < 0.5
                            ? HIGH_RISK_MCC[rnd.nextInt(HIGH_RISK_MCC.length)]
                            : LOW_RISK_MCC[rnd.nextInt(LOW_RISK_MCC.length)];

                    for (int b = 0; b < burst && made < txnsPerCustomer; b++) {
                        if (b > 0)
                            t += 30_000 + rnd.nextInt(240_000);

                        double amount = avgAmount * (5 + rnd.nextDouble() * 20);
                        double lat, lon;
                        String country;

                        if (goAbroad) {
                            lat = geo.lat() + rnd.nextGaussian() * 0.2;
                            lon = geo.lon() + rnd.nextGaussian() * 0.2;
                            country = geo.country();
                        } else {
                            lat = home.lat() + rnd.nextGaussian() * 0.6;
                            lon = home.lon() + rnd.nextGaussian() * 0.6;
                            country = home.country();
                        }

                        Transaction tx = new Transaction(
                                "TXN-" + customerId + "-" + seq++, customerId, amount,
                                "M-" + rnd.nextInt(50_000), mcc, t, lat, lon,
                                rnd.nextDouble() < 0.15, fraudDevice, country);

                        capture(feats, labels, tx, profile, 1);
                        profile.record(tx);
                        made++;
                    }
                }
            }
        }

        return toDataset(feats, labels);
    }

    private static Transaction legit(Random rnd, String customerId, int seq, City home,
            String homeDevice, double avgAmount, long t) {
        double amount = Math.max(1.0, avgAmount * Math.exp(rnd.nextGaussian() * 0.6));

        // Occasional large legitimate purchase: electronics, travel, wedding gifts.
        // ~3% of legit transactions are 8-30x the customer average.
        if (rnd.nextDouble() < 0.03) {
            amount = avgAmount * (8 + rnd.nextDouble() * 22);
        }

        double lat = home.lat() + rnd.nextGaussian() * 0.15;
        double lon = home.lon() + rnd.nextGaussian() * 0.15;
        String country = home.country();

        // occasional genuine cross-border purchase
        if (rnd.nextDouble() < 0.02) {
            country = CITIES[rnd.nextInt(CITIES.length)].country();
        }
        // occasional domestic travel
        if (rnd.nextDouble() < 0.03) {
            lat = home.lat() + rnd.nextGaussian() * 4;
            lon = home.lon() + rnd.nextGaussian() * 4;
        }

        String device = rnd.nextDouble() < 0.95 ? homeDevice : "DEV-N" + rnd.nextInt(100_000);
        String mcc = LOW_RISK_MCC[rnd.nextInt(LOW_RISK_MCC.length)];
        boolean cardPresent = rnd.nextDouble() < 0.85;

        return new Transaction("TXN-" + customerId + "-" + seq, customerId, amount,
                "M-" + rnd.nextInt(50_000), mcc, t, lat, lon, cardPresent, device, country);
    }

    private static void capture(List<double[]> feats, List<Integer> labels,
            Transaction tx, CustomerProfile profile, int label) {
        TxnContext ctx = TxnContext.of(tx, profile);
        feats.add(FeatureExtractor.extract(ctx));
        labels.add(label);
    }

    private static Dataset toDataset(List<double[]> feats, List<Integer> labels) {
        int n = feats.size();
        double[][] X = feats.toArray(new double[0][]);
        int[] y = new int[n];
        for (int i = 0; i < n; i++)
            y[i] = labels.get(i);
        return new Dataset(X, y);
    }

    private static int hourUtc(long t) {
        return (int) ((t / 3600_000L) % 24);
    }

    /** Container for feature matrix + labels. */
    public record Dataset(double[][] X, int[] y) {

        public int size() {
            return y.length;
        }

        public int positives() {
            int c = 0;
            for (int v : y)
                if (v == 1)
                    c++;
            return c;
        }

        /** Returns {train, val, test} with the given split fractions. */
        public Dataset[] split(double trainFrac, double valFrac, long seed) {
            int n = size();
            int[] idx = new int[n];
            for (int i = 0; i < n; i++)
                idx[i] = i;

            Random rnd = new Random(seed);
            for (int i = n - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int tmp = idx[i];
                idx[i] = idx[j];
                idx[j] = tmp;
            }

            int nTrain = (int) (n * trainFrac);
            int nVal = (int) (n * valFrac);

            return new Dataset[] {
                    take(idx, 0, nTrain),
                    take(idx, nTrain, nTrain + nVal),
                    take(idx, nTrain + nVal, n)
            };
        }

        private Dataset take(int[] idx, int from, int to) {
            int m = to - from;
            double[][] x = new double[m][];
            int[] labels = new int[m];
            for (int i = 0; i < m; i++) {
                int src = idx[from + i];
                x[i] = X[src];
                labels[i] = y[src];
            }
            return new Dataset(x, labels);
        }
    }
}