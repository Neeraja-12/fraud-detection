package com.fraud.fraud_detection.ml;

/**
 * Binary classification metrics. PR-AUC (average precision) is the
 * right headline metric for ~2% fraud rates — accuracy is misleading.
 */
public final class Metrics {

    public record Confusion(int tp, int fp, int tn, int fn) {
        public double precision() {
            return (tp + fp) == 0 ? 0.0 : (double) tp / (tp + fp);
        }

        public double recall() {
            return (tp + fn) == 0 ? 0.0 : (double) tp / (tp + fn);
        }

        public double f1() {
            double p = precision(), r = recall();
            return (p + r) == 0 ? 0.0 : 2 * p * r / (p + r);
        }

        public double accuracy() {
            int total = tp + fp + tn + fn;
            return total == 0 ? 0.0 : (double) (tp + tn) / total;
        }
    }

    private Metrics() {
    }

    public static Confusion confusion(int[] y, int[] pred) {
        int tp = 0, fp = 0, tn = 0, fn = 0;
        for (int i = 0; i < y.length; i++) {
            if (y[i] == 1 && pred[i] == 1)
                tp++;
            else if (y[i] == 0 && pred[i] == 1)
                fp++;
            else if (y[i] == 0 && pred[i] == 0)
                tn++;
            else
                fn++;
        }
        return new Confusion(tp, fp, tn, fn);
    }

    /** Area under the precision-recall curve (average precision). */
    public static double averagePrecision(double[] scores, int[] y) {
        int n = y.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++)
            idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(scores[b], scores[a]));

        int positives = 0;
        for (int v : y)
            if (v == 1)
                positives++;
        if (positives == 0)
            return 0.0;

        int tp = 0, fp = 0;
        double ap = 0.0;
        for (int i : idx) {
            if (y[i] == 1) {
                tp++;
                ap += (double) tp / (tp + fp);
            } else {
                fp++;
            }
        }
        return ap / positives;
    }

    public static int[] threshold(double[] scores, double t) {
        int[] pred = new int[scores.length];
        for (int i = 0; i < scores.length; i++)
            pred[i] = scores[i] >= t ? 1 : 0;
        return pred;
    }
}