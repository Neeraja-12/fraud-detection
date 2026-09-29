package com.fraud.fraud_detection.engine;

import com.fraud.fraud_detection.features.FeatureExtractor;
import com.fraud.fraud_detection.features.TxnContext;
import com.fraud.fraud_detection.ml.StandardScaler;
import com.fraud.fraud_detection.ml.TrainingPipeline;
import com.fraud.fraud_detection.model.AuditLog;
import com.fraud.fraud_detection.model.CustomerProfile;
import com.fraud.fraud_detection.model.Decision;
import com.fraud.fraud_detection.model.Transaction;
import com.fraud.fraud_detection.persistence.AuditLogRepository;
import com.fraud.fraud_detection.persistence.CustomerProfileRepository;
import com.fraud.fraud_detection.rules.RuleEngine;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FraudDetectionEngine {

    private static final double[] FALLBACK_WEIGHTS = {
            0.85, 1.20, 1.50, 0.60, 1.40,
           -0.30, 0.50, 1.10, 1.30, 1.60,
            1.55, -0.40, 0.90, 1.80
    };
    private static final double FALLBACK_BIAS = -3.2;
    private static final double FALLBACK_ML_WEIGHT = 0.65;
    private static final double FALLBACK_THRESHOLD = 0.55;
    private static final double REVIEW_FRACTION = 0.5;

    private double[] weights;
    private double bias;
    private double mlWeight;
    private double blockThreshold;
    private StandardScaler scaler;
    private String modelVersion = "fallback-v1";

    private final RuleEngine ruleEngine = new RuleEngine();
    private final CustomerProfileRepository repository;
    private final AuditLogRepository auditRepository;

    public FraudDetectionEngine(CustomerProfileRepository repository,
                                AuditLogRepository auditRepository) {
        this.repository = repository;
        this.auditRepository = auditRepository;
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
            this.modelVersion = "trained-" + shortHash(loaded.weights());
            System.out.println("[engine] loaded TRAINED model - " + loaded.summary()
                    + "  version=" + modelVersion);
        } else {
            this.weights = FALLBACK_WEIGHTS.clone();
            this.bias = FALLBACK_BIAS;
            this.mlWeight = FALLBACK_ML_WEIGHT;
            this.blockThreshold = FALLBACK_THRESHOLD;
            this.scaler = null;
            System.out.println("[engine] no trained model found - using hand-picked weights");
        }
    }

    private static String shortHash(double[] arr) {
        long h = 1125899906842597L;
        for (double v : arr) h = 31 * h + Double.doubleToLongBits(v);
        return Long.toHexString(h).substring(0, 8);
    }

    @Transactional
    public void register(CustomerProfile profile) {
        profile.syncForPersist();
        repository.save(profile);
    }

    public CustomerProfile getProfile(String customerId) {
        CustomerProfile profile = repository.findById(customerId).orElse(null);
        if (profile != null) profile.syncAfterLoad();
        return profile;
    }

    public record Assessment(
            String txnId, double mlProbability, double ruleScore,
            double riskScore, int riskPoints, Decision decision,
            List<RuleEngine.RuleHit> ruleHits,
            List<FeatureContribution> topFeatures) {}

    public record FeatureContribution(String feature, double contribution) {}

    @Transactional
    public Assessment assess(Transaction txn) {
        CustomerProfile profile = repository.findById(txn.customerId())
                .orElseGet(() -> new CustomerProfile(
                        txn.customerId(), txn.latitude(), txn.longitude(),
                        txn.countryCode(), 365));
        profile.syncAfterLoad();

        TxnContext ctx = TxnContext.of(txn, profile);
        double[] features = FeatureExtractor.extract(ctx);
        double[] modelInput = (scaler != null) ? scaler.transform(features) : features;
        double mlProb = mlProbability(modelInput);

        List<RuleEngine.RuleHit> hits = ruleEngine.evaluate(ctx);
        double ruleScore = ruleEngine.score(hits);

        double risk = mlWeight * mlProb + (1 - mlWeight) * ruleScore;
        Decision decision = decide(risk);
        int riskPoints = (int) Math.round(risk * 100);

        List<FeatureContribution> top = new ArrayList<>();
        for (int j = 0; j < features.length; j++) {
            top.add(new FeatureContribution(
                    FeatureExtractor.FEATURE_NAMES.get(j),
                    weights[j] * modelInput[j]));
        }
        top.sort((a, b) -> Double.compare(
                Math.abs(b.contribution()), Math.abs(a.contribution())));
        if (top.size() > 4) top = new ArrayList<>(top.subList(0, 4));

        String rulesCsv = hits.stream()
                .map(RuleEngine.RuleHit::rule)
                .collect(Collectors.joining(","));
        AuditLog audit = new AuditLog(
                txn.txnId(), txn.customerId(), txn.amount(),
                txn.merchantId(), txn.merchantCategory(), txn.countryCode(),
                decision, riskPoints, mlProb, ruleScore,
                rulesCsv, modelVersion);
        auditRepository.save(audit);

        if (decision != Decision.BLOCK) {
            profile.record(txn);
            profile.syncForPersist();
            repository.save(profile);
        } else {
            System.out.println("[engine] BLOCKED " + txn.txnId() + " - not recording in profile");
        }

        return new Assessment(txn.txnId(), mlProb, ruleScore, risk, riskPoints,
                decision, hits, top);
    }

    private double mlProbability(double[] x) {
        double z = bias;
        for (int j = 0; j < x.length; j++) z += weights[j] * x[j];
        return 1.0 / (1.0 + Math.exp(-z));
    }

    private Decision decide(double risk) {
        double review = blockThreshold * REVIEW_FRACTION;
        if (risk >= blockThreshold) return Decision.BLOCK;
        if (risk >= review)        return Decision.REVIEW;
        return Decision.ALLOW;
    }
}