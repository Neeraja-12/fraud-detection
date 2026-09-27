package com.fraud.fraud_detection.ml;

/**
 * Binary logistic regression trained with full-batch gradient descent,
 * L2 regularization and class weighting for the imbalanced fraud label.
 */
public class LogisticRegression {

    private double[] weights;
    private double bias;
    private final double l2;

    public LogisticRegression(int nFeatures, double l2) {
        this.weights = new double[nFeatures];
        this.bias = 0.0;
        this.l2 = l2;
    }

    public double probability(double[] x) {
        double z = bias;
        for (int j = 0; j < weights.length; j++)
            z += weights[j] * x[j];
        return sigmoid(z);
    }

    public void fit(double[][] X, int[] y,
            int iterations, double lr0, double positiveWeight) {
        int n = X.length;
        int d = weights.length;
        double[] grad = new double[d];

        for (int it = 0; it < iterations; it++) {
            java.util.Arrays.fill(grad, 0.0);
            double gradBias = 0.0;

            for (int i = 0; i < n; i++) {
                double p = probability(X[i]);
                double w = (y[i] == 1) ? positiveWeight : 1.0;
                double err = w * (y[i] - p);
                double[] xi = X[i];
                for (int j = 0; j < d; j++)
                    grad[j] += err * xi[j];
                gradBias += err;
            }

            double lr = lr0 / (1.0 + 0.002 * it); // simple decay
            for (int j = 0; j < d; j++) {
                weights[j] += lr * (grad[j] / n - l2 * weights[j]);
            }
            bias += lr * gradBias / n;
        }
    }

    public double[] weights() {
        return weights.clone();
    }

    public double bias() {
        return bias;
    }

    public void setParams(double[] weights, double bias) {
        this.weights = weights.clone();
        this.bias = bias;
    }

    private static double sigmoid(double z) {
        if (z >= 0) {
            double e = Math.exp(-z);
            return 1.0 / (1.0 + e);
        }
        double e = Math.exp(z);
        return e / (1.0 + e);
    }
}