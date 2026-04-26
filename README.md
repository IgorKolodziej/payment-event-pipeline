# Payment Event Processing Pipeline

Scala 3 team project for a local payment-event processing pipeline.

This repository contains a local payment-event processing pipeline with JSONL replay input, PostgreSQL-backed customer enrichment, MongoDB-backed processing history, risk assessment, and summary reporting. Internal planning notes are kept locally outside Git.

## Frozen Baseline

- JDK `25`
- Scala `3.3.7`
- sbt `1.12.5`
- Cats Effect
- FS2
- Circe
- Doobie
- PostgreSQL
- MongoDB
- MUnit

## Current Status

The repository has a working end-to-end local pipeline:

- build toolchain is configured,
- PostgreSQL and MongoDB start locally,
- PostgreSQL seed data loads,
- sample input data exists and is ordered for event-time replay,
- the app streams JSONL records through parsing, validation, enrichment, eligibility, risk, persistence, and summary aggregation,
- processed events, alerts, and eligibility violations are written to MongoDB,
- formatting works,
- tests run.

## Team

- Igor Kołodziej
- Hubert Kowalski
- Kacper Wadas
- Oliwia Strzechowska
- Roksana Rogalska

## Local Setup

Requirements:

- JDK `25`
- sbt `1.12.5`
- Docker
- Docker Compose

First-time setup:

```bash
cp .env.example .env
docker compose up -d
set -a && source .env && set +a && sbt run
```

Expected default run shape:

```text
Payment Event Processing Pipeline started. input=sample-data/small_events.jsonl, output=out
Payment Event Processing Pipeline finished. read=200, processed=183, rejected=17, alerts=102
```

Useful commands:

```bash
sbt test
sbt scalafmt
sbt Test/scalafmt
docker compose ps -a
docker compose logs postgres
docker compose logs mongo
docker compose down
docker compose down -v
```

Open PostgreSQL shell:

```bash
docker exec -it pep-postgres psql -U pipeline_user -d payment_pipeline
```

Open Mongo shell:

```bash
docker compose exec mongo mongosh
```

## Replay Input Modes

The app reads events through an `EventSource` abstraction. The current local sources are file-based:

- `file`: fast JSONL replay, used by default.
- `paced-file`: JSONL replay with a fixed delay between records, useful for simulating incrementally arriving events during demos or dashboard refresh work.

Configure the mode in `src/main/resources/application.conf`:

```hocon
app {
  inputFile = "sample-data/small_events.jsonl"
  outputDir = "out"
  inputMode = "file"
  streamDelayMillis = 0
}
```

For paced replay, use:

```hocon
app {
  inputMode = "paced-file"
  streamDelayMillis = 250
}
```

The pacing is implemented at the source boundary with FS2. The processing pipeline itself contains no sleeps and does not know whether records came from fast file replay or paced replay.

Replay input should be ordered by event timestamp ascending. Risk context is based on already processed events, so event-time ordering matters for deterministic replay.

## Mongo Storage (Risk History)

This project uses MongoDB as a replayable read/write model for risk history.

Default collections:

- `processed_transactions` (idempotent upsert by `eventId`)
- `eligibility_violations` (idempotent upsert by `(eventId, violationType)`)
- `alerts` (idempotent upsert by `(eventId, alertType)`)

Recommended indexes:

```javascript
use payment_pipeline

db.processed_transactions.createIndex({ eventId: 1 }, { unique: true })
db.processed_transactions.createIndex({ customerId: 1, timestamp: 1 })
db.processed_transactions.createIndex({ customerId: 1, deviceId: 1 })

db.eligibility_violations.createIndex({ eventId: 1, violationType: 1 }, { unique: true })
db.eligibility_violations.createIndex({ customerId: 1 })

db.alerts.createIndex({ eventId: 1, alertType: 1 }, { unique: true })
db.alerts.createIndex({ customerId: 1 })
```

> create them manually in `mongosh`

Manual verification:

```javascript
// Verify idempotency: rerun pipeline and ensure no duplicates by eventId
db.processed_transactions.aggregate([
    { $group: { _id: "$eventId", c: { $sum: 1 } } },
    { $match: { c: { $gt: 1 } } }
])

// Inspect one customer history ordered by event-time
db.processed_transactions.find({ customerId: 10 }).sort({ timestamp: 1 })
```

## Repository Notes

- `.env` is local and must not be committed.
- `scripts/seed_postgres.sql` seeds the local customer table.
- `sample-data/` contains example input data.
- `out/` is used for generated run artifacts.
- internal planning and issue notes are local-only and must not be committed.

## Current Repository Layout

```text
payment-event-pipeline/
├── build.sbt
├── docker-compose.yml
├── .env.example
├── .scalafmt.conf
├── scripts/
│   └── seed_postgres.sql
├── sample-data/
│   └── small_events.jsonl
├── out/
│   └── .gitkeep
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.conf
    │   └── scala/com/team/pipeline/
    │       ├── Main.scala
    │       └── config/
    │           └── AppConfig.scala
    └── test/
        └── scala/com/team/pipeline/
            ├── PilotTest.scala
            └── config/
                └── AppConfigTest.scala
```

## Workflow Rules

- Work on short-lived feature branches.
- Keep commits focused and readable.
- Format before committing.
- Run tests before opening a PR.
- Do not commit secrets or generated output files.
- Keep code changes small, tested, and aligned with the agreed project scope.

## License

Apache License 2.0.
