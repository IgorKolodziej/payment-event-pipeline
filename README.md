# Payment Event Processing Pipeline
Scala application that processes payment events.

The system will:
- read input events from JSONL files
- parse and validate records
- normalize data
- enrich events with customer data from PostgreSQL
- detect anomalies / risk signals
- save processed results to MongoDB
- generate a summary report
- later expose basic statistics for a dashboard

## Tech stack
- Scala 3
- sbt
- Cats Effect
- FS2
- Circe
- Doobie
- PostgreSQL
- MongoDB
- MUnit

## Team
- Igor Kołodziej
- Hubert Kowalski
- Kacper Wadas
- Oliwia Strzechowska
- Roksana Rogalska

## Repository setup
Before you start coding, make sure you have installed:
- JDK 17+
- sbt
- Docker + Docker Compose

## First-time local setup
1. Copy environment variables:
```bash
cp .env.example .env
```

2. Start local databases:

```bash
docker compose up -d
```

3. Run the application:

```bash
set -a && source .env && set +a && sbt run
```

4. Run tests:

```bash
sbt test
```

5. Format the code:

```bash
sbt scalafmt
sbt Test/scalafmt
```

## Useful commands

Start databases:

```bash
docker compose up -d
```

Stop databases:

```bash
docker compose down
```

Stop databases and remove volumes:

```bash
docker compose down -v
```

Check running containers:

```bash
docker compose ps -a
```

Show logs:

```bash
docker compose logs postgres
docker compose logs mongo
```

Open PostgreSQL shell:

```bash
docker exec -it pep-postgres psql -U pipeline_user -d payment_pipeline
```

Open Mongo shell:

```bash
docker compose exec mongo mongosh
```

## Important notes
* `.env` is local and should **not** be committed.
* `.env.example` stays in the repository.
* PostgreSQL seed data is loaded from `scripts/seed_postgres.sql`.
* Sample input data is stored in `sample-data/`.

## Project structure

```text
payment-event-pipeline/
├── build.sbt
├── docker-compose.yml
├── .env.example
├── .scalafmt.conf
├── scripts/
│   └── seed_postgres.sql
├── sample-data/
├── src/
│   ├── main/
│   │   ├── resources/
│   │   │   └── application.conf
│   │   └── scala/com/team/pipeline/
│   │       ├── Main.scala
│   │       ├── domain/
│   │       ├── parsing/
│   │       ├── validation/
│   │       ├── customer/
│   │       ├── risk/
│   │       ├── persistence/
│   │       ├── reporting/
│   │       └── pipeline/
│   └── test/
│       └── scala/com/team/pipeline/
```

## Workflow rules

* Work on your own feature branch, not directly on `main`.
* Keep commits small and readable.
* Format code before committing.
* Run tests before pushing.
* Do not commit local secrets or random generated files.
* If you change shared models or contracts, tell the team first.

## License
This project is licensed under the Apache License 2.0.
