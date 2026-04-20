# Payment Event Processing Pipeline

Scala 3 team project for a local payment-event processing pipeline.

This repository currently contains a verified development scaffold plus the implementation contract in [project-spec.md](project-spec.md). The pipeline itself is still being implemented.

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

The repository is ready for implementation work:

- build toolchain is configured,
- PostgreSQL and MongoDB start locally,
- PostgreSQL seed data loads,
- sample input data exists,
- formatting works,
- tests run,
- the current app entry point is a placeholder.

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

## Repository Notes

- `.env` is local and must not be committed.
- `scripts/seed_postgres.sql` seeds the local customer table.
- `sample-data/` contains example input data.
- `out/` is used for generated run artifacts.
- `project-spec.md` is the implementation source of truth.

## Current Repository Layout

```text
payment-event-pipeline/
├── build.sbt
├── docker-compose.yml
├── .env.example
├── .scalafmt.conf
├── project-spec.md
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
    │       └── Main.scala
    └── test/
        └── scala/com/team/pipeline/
            └── PilotTest.scala
```

## Workflow Rules

- Work on short-lived feature branches.
- Keep commits focused and readable.
- Format before committing.
- Run tests before opening a PR.
- Do not commit secrets or generated output files.
- Keep the code aligned with `project-spec.md`.

## License

Apache License 2.0.
