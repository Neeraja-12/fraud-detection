package com.fraud.fraud_detection.ml;

import com.fraud.fraud_detection.features.FeatureExtractor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Trains a logistic-regression fraud model end-to-end, saves weights to
 * ./data/trained-model.txt, and reports test-set metrics.
 */
@Service
public class TrainingPipeline {

    public static final Path MODEL_FILE = Paths.get("./data/trained-model.txt");

    private static final int N_CUSTOMERS = 500;
    private static final int TXNS_PER_CUST = 60;
    private static final double FRAUD_RATE = 0.02;
    private static final long DATA_SEED = 42L;
    private static final long SPLIT_SEED = 7L;

    public record TrainResult(
            int trainSize, int valSize, int testSize,
            int positives,
            double precision, double recall, double f1,
            double prAucRules, double prAucModel, double prAucBlended,
            double mlWeight, double threshold,
            double[] weights, double bias,
            double[] scalerMean, double[] scalerStd,
            long elapsedMs) {
    }

    public TrainResult run() {
        long start = System.currentTimeMillis();

        // 1. Generate data
        System.out.println("[train] generating synthetic data...");
        SyntheticDataGenerator.Dataset data = SyntheticDataGenerator.generate(
                N_CUSTOMERS, TXNS_PER_CUST, FRAUD_RATE, DATA_SEED);
        System.out.printf("[train] generated %,d transactions (%,d fraud, %.2f%%)%n",
                data.size(), data.positives(), 100.0 * data.positives() / data.size());

        // 2. Split
        SyntheticDataGenerator.Dataset[] parts = data.split(0.70, 0.15, SPLIT_SEED);
        SyntheticDataGenerator.Dataset train = parts[0];
        SyntheticDataGenerator.Dataset val = parts[1];
        SyntheticDataGenerator.Dataset test = parts[2];

        System.out.printf("[train] split: train=%,d  val=%,d  test=%,d%n",
                train.size(), val.size(), test.size());

        // 3. Fit scaler on training data only
        StandardScaler scaler = new StandardScaler();
        scaler.fit(train.X());
        double[][] Xtr = scaler.transform(train.X());

        // 4. Class weight for imbalanced data
        int pos = train.positives();
        int neg = train.size() - pos;
        double positiveWeight = Math.min(20.0, (double) neg / Math.max(1, pos));
        System.out.printf("[train] class balance: pos=%,d neg=%,d (weight=%.2f)%n",
                pos, neg, positiveWeight);

        // 5. Train
        System.out.println("[train] fitting logistic regression...");
        LogisticRegression lr = new LogisticRegression(FeatureExtractor.N_FEATURES, 1e-3);
        lr.fit(Xtr, train.y(), 1500, 0.5, positiveWeight);

        // 6. Tune blend weight + threshold on validation set
        double[] valMl = new double[val.size()];
        for (int i = 0; i < val.size(); i++) {
            valMl[i] = lr.probability(scaler.transform(val.X()[i]));
        }

        double[] WEIGHT_GRID = { 0.0, 0.25, 0.5, 0.65, 0.8, 1.0 };
        double bestF1 = -1.0, bestW = 0.65, bestT = 0.5;

        for (double w : WEIGHT_GRID) {
            for (double t = 0.05; t < 0.95; t += 0.01) {
                int[] pred = new int[val.size()];
                for (int i = 0; i < val.size(); i++) {
                    double s = w * valMl[i] + (1 - w) * 0.0; // rules not evaluated here
                    pred[i] = s >= t ? 1 : 0;
                }
                double f1 = Metrics.confusion(val.y(), pred).f1();
                if (f1 > bestF1) {
                    bestF1 = f1;
                    bestW = w;
                    bestT = t;
                }
            }
        }
        System.out.printf("[train] tuned: mlWeight=%.2f  threshold=%.2f  valF1=%.3f%n",
                bestW, bestT, bestF1);

        // 7. Evaluate on test set
        double[] testMl = new double[test.size()];
        for (int i = 0; i < test.size(); i++) {
            testMl[i] = lr.probability(scaler.transform(test.X()[i]));
        }
        int[] pred = Metrics.threshold(testMl, bestT);
        Metrics.Confusion cm = Metrics.confusion(test.y(), pred);
        double prAuc = Metrics.averagePrecision(testMl, test.y());

        System.out.printf("[train] test precision=%.3f  recall=%.3f  f1=%.3f  pr-auc=%.3f%n",
                cm.precision(), cm.recall(), cm.f1(), prAuc);

        // 8. Save to disk
        try {
            save(lr, scaler, bestW, bestT);
            System.out.println("[train] saved weights to " + MODEL_FILE.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[train] could not save model: " + e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - start;

        return new TrainResult(
                train.size(), val.size(), test.size(),
                data.positives(),
                cm.precision(), cm.recall(), cm.f1(),
                0.0, prAuc, 0.0,
                bestW, bestT,
                lr.weights(), lr.bias(),
                scaler.mean(), scaler.std(),
                elapsed);
    }

    /** Writes the model as plain text — easy to inspect and diff. */
    public static void save(LogisticRegression lr, StandardScaler scaler,
            double mlWeight, double threshold) throws IOException {
        Files.createDirectories(MODEL_FILE.getParent());

        StringBuilder sb = new StringBuilder();
        sb.append("# Fraud model — trained by TrainingPipeline\n");
        sb.append("# format: key=value  (values are comma-separated doubles where applicable)\n");
        sb.append("mlWeight=").append(mlWeight).append('\n');
        sb.append("threshold=").append(threshold).append('\n');
        sb.append("bias=").append(lr.bias()).append('\n');
        sb.append("weights=").append(join(lr.weights())).append('\n');
        sb.append("scalerMean=").append(join(scaler.mean())).append('\n');
        sb.append("scalerStd=").append(join(scaler.std())).append('\n');

        Files.writeString(MODEL_FILE, sb.toString());
    }

    private static String join(double[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0)
                sb.append(',');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /** Loads a previously-saved model. Returns null if not present. */
    public static LoadedModel load() {
        if (!Files.exists(MODEL_FILE))
            return null;
        try {
            double mlWeight = 0, threshold = 0, bias = 0;
            double[] weights = null, mean = null, std = null;

            for (String line : Files.readAllLines(MODEL_FILE)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                int eq = line.indexOf('=');
                if (eq < 0)
                    continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                switch (key) {
                    case "mlWeight" -> mlWeight = Double.parseDouble(val);
                    case "threshold" -> threshold = Double.parseDouble(val);
                    case "bias" -> bias = Double.parseDouble(val);
                    case "weights" -> weights = parse(val);
                    case "scalerMean" -> mean = parse(val);
                    case "scalerStd" -> std = parse(val);
                }
            }

            if (weights == null || mean == null || std == null)
                return null;
            return new LoadedModel(weights, bias, mean, std, mlWeight, threshold);
        } catch (Exception e) {
            System.err.println("[train] failed to load model: " + e.getMessage());
            return null;
        }
    }

    private static double[] parse(String csv) {
        String[] parts = csv.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++)
            out[i] = Double.parseDouble(parts[i]);
        return out;
    }

    public record LoadedModel(
            double[] weights, double bias,
            double[] scalerMean, double[] scalerStd,
            double mlWeight, double threshold) {

        public String summary() {
            return "mlWeight=%.2f threshold=%.2f bias=%.2f weights=%d values"
                    .formatted(mlWeight, threshold, bias, weights.length);
        }
    }
}