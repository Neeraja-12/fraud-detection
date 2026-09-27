package com.fraud.fraud_detection.engine;

import com.fraud.fraud_detection.features.FeatureExtractor;
import com.fraud.fraud_detection.features.TxnContext;
import com.fraud.fraud_detection.ml.StandardScaler;
import com.fraud.fraud_detection.ml.TrainingPipeline;
import com.fraud.fraud_detection.model.CustomerProfile;
import com.fraud.fraud_detection.model.Decision;
import com.fraud.fraud_detection.model.Transaction;
import com.fraud.fraud_detection.persistence.CustomerProfileRepository;
import com.fraud.fraud_detection.rules.RuleEngine;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-time scoring engine. Wires features + model + rules together
 * and persists profiles to H2 via Spring Data JPA.
 *
 * On startup, tries to load a trained model from ./data/trained-model.txt.
 * If not present, falls back to hand-picked weights.
 */
@Service
public class FraudDetectionEngine {

    // ---------------------------------------------------------------
    // Fallback (hand-picked) parameters — used only if no trained model
    // ---------------------------------------------------------------
    private static final double[] FALLBACK_WEIGHTS = {
            0.85, 1.20, 1.50, 0.60, 1.40,
            -0.30, 0.50, 1.10, 1.30, 1.60,
            1.55, -0.40, 0.90, 1.80
    };
    private static final double FALLBACK_BIAS = -3.2;
    private static final double FALLBACK_ML_WEIGHT = 0.65;
    private static final double FALLBACK_THRESHOLD = 0.55;

    private static final double REVIEW_FRACTION = 0.5;

    // ---------------------------------------------------------------
    // Active model parameters (may be replaced by trained values)
    // ---------------------------------------------------------------
    private double[] weights;
    private double bias;
    private double mlWeight;
    private double blockThreshold;
    private StandardScaler scaler; // null = no scaling

    private final RuleEngine ruleEngine = new RuleEngine();
    private final CustomerProfileRepository repository;

    public FraudDetectionEngine(CustomerProfileRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        TrainingPipeline.LoadedModel loaded = TrainingPipeline.load();
        if (loaded != null) {
            this.weights = loaded.weights();
            this.bias = loaded.bias();
            this.mlWeight = loaded.mlWeight();
            this.blockThreshold = loaded.threshold();

            StandardScaler s = new StandardScaler();
            s.setParams(loaded.scalerMean(), loaded.scalerStd());
            this.scaler = s;

            System.out.println("[engine] loaded TRAINED model — " + loaded.summary());
        } else {
            this.weights = FALLBACK_WEIGHTS.clone();
            this.bias = FALLBACK_BIAS;
            this.mlWeight = FALLBACK_ML_WEIGHT;
            this.blockThreshold = FALLBACK_THRESHOLD;
            this.scaler = null;

            System.out.println("[engine] no trained model found — using hand-picked weights");
        }
    }

    /** Register (or overwrite) a customer profile. */
    @Transactional
    public void register(CustomerProfile profile) {
        profile.syncForPersist();
        repository.save(profile);
    }

    public CustomerProfile getProfile(String customerId) {
        CustomerProfile profile = repository.findById(customerId).orElse(null);
        if (profile != null) {
            profile.syncAfterLoad();
        }
        return profile;
    }

    public record Assessment(
            String txnId,
            double mlProbability,
            double ruleScore,
            double riskScore,
            int riskPoints,
            Decision decision,
            List<RuleEngine.RuleHit> ruleHits,
            List<FeatureContribution> topFeatures) {
    }

    public record FeatureContribution(String feature, double contribution) {
    }

    @Transactional
    public Assessment assess(Transaction txn) {
        // 1. Load profile from DB, or create a cold-start profile
        CustomerProfile profile = repository.findById(txn.customerId())
                .orElseGet(() -> new CustomerProfile(
                        txn.customerId(),
                        txn.latitude(),
                        txn.longitude(),
                        txn.countryCode(),
                        365));

        profile.syncAfterLoad();

        // 2. Derive context (BEFORE updating profile)
        TxnContext ctx = TxnContext.of(txn, profile);

        // 3. Features
        double[] features = FeatureExtractor.extract(ctx);

        // 4. Model probability (with scaling if trained)
        double[] modelInput = (scaler != null) ? scaler.transform(features) : features;
        double mlProb = mlProbability(modelInput);

        // 5. Rules
        List<RuleEngine.RuleHit> hits = ruleEngine.evaluate(ctx);
        double ruleScore = ruleEngine.score(hits);

        // 6. Blend
        double risk = mlWeight * mlProb + (1 - mlWeight) * ruleScore;
        Decision decision = decide(risk);

        // 7. Top feature contributions (in scaled space, matching what the model saw)
        double[] contribInput = (scaler != null) ? scaler.transform(features) : features;
        List<FeatureContribution> top = new ArrayList<>();
        for (int j = 0; j < features.length; j++) {
            top.add(new FeatureContribution(
                    FeatureExtractor.FEATURE_NAMES.get(j),
                    weights[j] * contribInput[j]));
        }
        top.sort((a, b) -> Double.compare(
                Math.abs(b.contribution()), Math.abs(a.contribution())));
        if (top.size() > 4) {
            top = new ArrayList<>(top.subList(0, 4));
        }

        // 8. Record, sync, persist
        // 8. Record, sync, persist — but ONLY for non-BLOCK decisions
        // (a blocked transaction never happened, so it must not
        // contaminate the customer's behavioral history)
        if (decision != Decision.BLOCK) {
            profile.record(txn);
            profile.syncForPersist();
            repository.save(profile);
        } else {
            System.out.printf("[engine] BLOCKED %s — not recording in profile%n", txn.txnId());
        }

        return new Assessment(
                txn.txnId(),
                mlProb,
                ruleScore,
                risk,
                (int) Math.round(risk * 100),
                decision,
                hits,
                top);
    }

    private double mlProbability(double[] x) {
        double z = bias;
        for (int j = 0; j < x.length; j++) {
            z += weights[j] * x[j];
        }
        return 1.0 / (1.0 + Math.exp(-z));
    }

    private Decision decide(double risk) {
        double review = blockThreshold * REVIEW_FRACTION;
        if (risk >= blockThreshold)
            return Decision.BLOCK;
        if (risk >= review)
            return Decision.REVIEW;
        return Decision.ALLOW;
    }
}