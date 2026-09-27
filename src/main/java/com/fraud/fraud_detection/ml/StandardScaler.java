package com.fraud.fraud_detection.ml;

/**
 * Z-score normalizer. Fit on training data only, then used to transform
 * train/val/test identically. Also serializable for persistence.
 */
public class StandardScaler {

    private double[] mean;
    private double[] std;
    private boolean fitted = false;

    public void fit(double[][] X) {
        int n = X.length;
        int d = X[0].length;
        mean = new double[d];
        std = new double[d];

        for (double[] row : X) {
            for (int j = 0; j < d; j++)
                mean[j] += row[j];
        }
        for (int j = 0; j < d; j++)
            mean[j] /= n;

        for (double[] row : X) {
            for (int j = 0; j < d; j++) {
                double diff = row[j] - mean[j];
                std[j] += diff * diff;
            }
        }
        for (int j = 0; j < d; j++) {
            std[j] = Math.sqrt(std[j] / n);
            if (std[j] < 1e-9)
                std[j] = 1.0; // constant feature
        }
        fitted = true;
    }

    public double[] transform(double[] x) {
        if (!fitted)
            throw new IllegalStateException("StandardScaler not fitted");
        double[] out = new double[x.length];
        for (int j = 0; j < x.length; j++)
            out[j] = (x[j] - mean[j]) / std[j];
        return out;
    }

    public double[][] transform(double[][] X) {
        double[][] out = new double[X.length][];
        for (int i = 0; i < X.length; i++)
            out[i] = transform(X[i]);
        return out;
    }

    public double[] mean() {
        return mean.clone();
    }

    public double[] std() {
        return std.clone();
    }

    /** Restore a previously-fitted scaler from saved parameters. */
    public void setParams(double[] mean, double[] std) {
        this.mean = mean.clone();
        this.std = std.clone();
        this.fitted = true;
    }
}