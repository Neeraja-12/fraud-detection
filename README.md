$content = @'
# 🛡️ AI Banking Fraud Detection

[![CI](https://github.com/Neeraja-12/fraud-detection/actions/workflows/ci.yml/badge.svg)](https://github.com/Neeraja-12/fraud-detection/actions/workflows/ci.yml)

## 🔗 Live Demo

**[https://fraud-detection-jq8x.onrender.com](https://fraud-detection-jq8x.onrender.com)**

Open the dashboard, click the **Fraud** preset, and watch the engine block an ₹85,000 international transaction in real time.

Interactive API docs: [https://fraud-detection-jq8x.onrender.com/swagger-ui.html](https://fraud-detection-jq8x.onrender.com/swagger-ui.html)

---

A real-time fraud detection service built with **Java 17**, **Spring Boot 4.1.1**, and a **trained logistic regression model**. Scores every transaction in milliseconds, explains every decision with rule reason codes and ML feature contributions, and integrates with any banking application via a secure REST API.

## The Flow

Transaction
  ↓
Java Spring Boot (this service)
  ↓
AI/ML Model (trained logistic regression + rule engine)
  ↓
Fraud Probability
  ↓
Normal Transaction / Suspicious Transaction / Blocked

### Example — ₹85,000 at 2:15 AM from London

Transaction Amount:  ₹85,000
Location:            London, GB (home: Mumbai, IN)
Time:                2:15 AM IST
Previous pattern:    ₹1,000 – ₹5,000

  ↓  AI Analysis  ↓

🔴 HIGH-RISK — BLOCK (risk 100/100)

## What It Does

Every transaction is scored against **14 engineered features** and **13 deterministic rules**, then blended into a single risk score:

| Layer | Purpose |
|---|---|
| Feature extraction | 14 numeric signals (velocity, geo, device, MCC, amount deviation) |
| ML model | Trained logistic regression → fraud probability |
| Rule engine | 13 domain rules → explainable reason codes |
| Blend | ML + rules → final risk |
| Decision | ALLOW / REVIEW / BLOCK |

Every response includes **full explainability**:
- Which rules fired, with human-readable details
- Which features contributed most to the ML score
- Combined risk points (0–100)

## Tests

The project has **14 JUnit tests** covering rule logic, feature extraction, and end-to-end scoring.

    mvn test

Expected: Tests run: 14, Failures: 0, Errors: 0, Skipped: 0

CI runs these tests automatically on every push.

## Run Locally

Prerequisites:
- Java 17+
- Maven 3.6+

    git clone https://github.com/Neeraja-12/fraud-detection.git
    cd fraud-detection
    mvn spring-boot:run

Then open:
- Dashboard: http://localhost:9090/
- Swagger UI: http://localhost:9090/swagger-ui.html

## Run with Docker

Requires Docker Desktop.

    docker compose up

The service is available at http://localhost:9090.

## API Reference

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| /api/health | GET | No | Liveness check |
| /api/customers | POST | Yes | Register customer profile |
| /api/customers/{id} | GET | Yes | Look up profile |
| /api/score | POST | Yes | Score transaction |
| /api/admin/train | POST | Yes | Retrain model |
| /api/admin/model-info | POST | Yes | Check model file |

All /api/** requests need header: X-API-Key: demo-key-123

## Sample Requests

The requests/ folder contains ready-to-use JSON files:

| File | Purpose | Expected decision |
|---|---|---|
| normal-transaction.json | ₹1,500 Mumbai purchase | ALLOW |
| suspicious-transaction.json | ₹30,000 card-not-present | REVIEW |
| fraud-transaction.json | ₹85,000 London, 2 AM | BLOCK |

Example:

    curl.exe -X POST http://localhost:9090/api/score -H "Content-Type: application/json" -H "X-API-Key: demo-key-123" -d "@requests/fraud-transaction.json"

## The 14 Features

| # | Feature | Description |
|---|---|---|
| 0 | log_amount | log(1 + amount) |
| 1 | amount_vs_customer_avg | amount / mean(history) |
| 2 | txns_last_1h | velocity |
| 3 | txns_last_24h | velocity |
| 4 | is_foreign | country mismatch |
| 5 | is_card_present | card present flag |
| 6 | is_odd_hour | UTC 00:00–06:00 |
| 7 | distance_from_home_km | haversine from home |
| 8 | log_implied_speed_kmph | distance / time since last txn |
| 9 | high_risk_mcc | MCC in {7995, 6051, 5967, 4829, 5966} |
| 10 | new_device | device not in profile |
| 11 | log_account_age_days | account maturity |
| 12 | failed_attempts_24h | auth failures |
| 13 | cross_border_cnp | foreign + card-not-present |

## The 13 Rules

| Rule | Trigger | Weight |
|---|---|---|
| HIGH_AMOUNT | amount > 5000 | 2.0 |
| AMOUNT_ANOMALY | amount > 8x customer avg | 2.5 |
| VELOCITY_1H | >= 3 txns last hour | 2.0 |
| VELOCITY_24H | >= 8 txns last 24h | 1.2 |
| CROSS_BORDER | different country | 1.5 |
| CROSS_BORDER_CNP | foreign + card-not-present | 2.0 |
| ODD_HOURS | UTC 00:00–06:00 | 0.8 |
| NEW_DEVICE | unseen device | 1.5 |
| HIGH_RISK_MCC | risky merchant category | 1.8 |
| IMPOSSIBLE_TRAVEL | implied speed > 900 km/h | 3.0 |
| FAR_FROM_HOME | distance > 500 km | 1.0 |
| NEW_ACCOUNT_BIG_TXN | account < 30d + amount > 1000 | 1.2 |
| AUTH_FAILURES | >= 3 failures in 24h | 1.5 |

## Retraining the Model

    curl -X POST http://localhost:9090/api/admin/train -H "X-API-Key: demo-key-123"

Takes about 1 second. Overwrites ./data/trained-model.txt.

## Design Decisions

**Why logistic regression, not deep learning?**
Transparent, fast (< 1 ms), explainable, and easy to update. For tabular fraud data with engineered features, it rivals deep models.

**Why rules + ML, not ML alone?**
Rules cover cold-start customers, regulatory requirements, and known fraud typologies. The ML model handles nuanced cases rules cannot express.

**Why not record BLOCKed transactions?**
A blocked transaction never happened. Recording it would poison the profile and cause false positives on the next legitimate transaction.

**Why H2?**
Zero setup, single-file storage, includes a web console. Drop-in replacement with PostgreSQL by changing the JDBC URL.

## Tech Stack

| Component | Version |
|---|---|
| Java | 17 |
| Spring Boot | 4.1.1 |
| Spring Security | API key auth |
| Spring Data JPA | H2 persistence |
| H2 Database | 2.4.240 |
| Maven | 3.9.16 |
| Docker | Multi-stage build |
| GitHub Actions | CI + keep-alive |
| Render | Free-tier deployment |

## Roadmap

- [x] Trained ML model
- [x] JUnit tests (14)
- [x] Swagger/OpenAPI docs
- [x] Docker packaging
- [x] GitHub Actions CI
- [x] Deployed to Render (live URL)
- [ ] PostgreSQL for production
- [ ] Kafka streaming ingestion
- [ ] Prometheus + Grafana monitoring
- [ ] Model drift detection

## Limitations

- Training data is synthetic — real data will score lower on PR-AUC
- No rate limiting on /api/score
- API keys are plain text
- No audit log
- Free tier: H2 data resets on redeploy, app sleeps after inactivity

## License

MIT — use freely, attribution appreciated.

---

Built as a demonstration of production-shaped ML engineering with Java + Spring Boot.
'@
Set-Content -Path README.md -Value $content -Encoding UTF8 -NoNewline
Write-Host "README.md rewritten."