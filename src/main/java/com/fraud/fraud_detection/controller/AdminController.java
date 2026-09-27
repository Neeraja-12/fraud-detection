package com.fraud.fraud_detection.controller;

import com.fraud.fraud_detection.ml.TrainingPipeline;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin endpoints. Requires X-API-Key (same as everything else under /api/**).
 * In production, gate these behind a separate admin key or role.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TrainingPipeline pipeline;

    public AdminController(TrainingPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /** Trigger end-to-end training. Saves ./data/trained-model.txt. */
    @PostMapping("/train")
    public Map<String, Object> train() {
        long t0 = System.currentTimeMillis();
        TrainingPipeline.TrainResult r = pipeline.run();
        long elapsed = System.currentTimeMillis() - t0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "trained");
        out.put("trainSize", r.trainSize());
        out.put("valSize", r.valSize());
        out.put("testSize", r.testSize());
        out.put("positives", r.positives());
        out.put("precision", r.precision());
        out.put("recall", r.recall());
        out.put("f1", r.f1());
        out.put("prAuc", r.prAucModel());
        out.put("mlWeight", r.mlWeight());
        out.put("threshold", r.threshold());
        out.put("elapsedMs", elapsed);
        return out;
    }

    /** Check if a trained model file exists on disk. */
    @PostMapping("/model-info")
    public Map<String, Object> modelInfo() {
        TrainingPipeline.LoadedModel m = TrainingPipeline.load();
        Map<String, Object> out = new LinkedHashMap<>();
        if (m == null) {
            out.put("trained", false);
            out.put("message", "No ./data/trained-model.txt — using hardcoded weights");
        } else {
            out.put("trained", true);
            out.put("summary", m.summary());
        }
        return out;
    }
}