package com.team.pipeline.config

import com.typesafe.config.ConfigFactory
import munit.FunSuite

import java.nio.file.Path
import scala.concurrent.duration.*

class AppConfigTest extends FunSuite:
  test("loads typed app config from HOCON") {
    val config = ConfigFactory.parseString(
      """
        |app {
        |  inputFile = "sample-data/events.jsonl"
        |  outputDir = "out"
        |  emailSalt = "test-salt"
        |  inputMode = "file"
        |  streamDelayMillis = 0
        |}
        |
        |postgres {
        |  host = "localhost"
        |  port = 5432
        |  database = "payment_pipeline"
        |  user = "pipeline_user"
        |  password = "pipeline_pass_dev"
        |  driver = "org.postgresql.Driver"
        |}
        |
        |mongo {
        |  host = "localhost"
        |  port = 27017
        |  database = "payment_pipeline"
        |  processedCollection = "processed_transactions"
        |  alertsCollection = "alerts"
        |  violationsCollection = "eligibility_violations"
        |}
        |
        |kafka {
        |  bootstrapServers = "localhost:19092"
        |  topic = "payment-events"
        |  groupId = "payment-event-pipeline"
        |  clientId = "payment-event-pipeline-test"
        |}
        |""".stripMargin
    )

    val appConfig = AppConfig.fromConfig(config)

    assertEquals(appConfig.app.inputFile, Path.of("sample-data/events.jsonl"))
    assertEquals(appConfig.app.outputDir, Path.of("out"))
    assertEquals(appConfig.app.emailSalt, "test-salt")
    assertEquals(appConfig.app.inputMode, InputMode.File)
    assertEquals(appConfig.app.streamDelay, 0.millis)
    assertEquals(appConfig.postgres.port, 5432)
    assertEquals(appConfig.postgres.database, "payment_pipeline")
    assertEquals(appConfig.mongo.port, 27017)
    assertEquals(appConfig.mongo.processedCollection, "processed_transactions")
    assertEquals(appConfig.mongo.alertsCollection, "alerts")
    assertEquals(appConfig.mongo.violationsCollection, "eligibility_violations")
    assertEquals(appConfig.kafka.bootstrapServers, "localhost:19092")
    assertEquals(appConfig.kafka.topic, "payment-events")
    assertEquals(appConfig.kafka.groupId, "payment-event-pipeline")
    assertEquals(appConfig.kafka.clientId, "payment-event-pipeline-test")
    assert(!appConfig.postgres.toString.contains("pipeline_pass_dev"))
    assert(appConfig.postgres.toString.contains("password=****"))
  }

  test("loads paced file input mode") {
    val config = ConfigFactory.parseString(
      """
        |app {
        |  inputFile = "sample-data/events.jsonl"
        |  outputDir = "out"
        |  emailSalt = "test-salt"
        |  inputMode = "paced-file"
        |  streamDelayMillis = 250
        |}
        |
        |postgres {
        |  host = "localhost"
        |  port = 5432
        |  database = "payment_pipeline"
        |  user = "pipeline_user"
        |  password = "pipeline_pass_dev"
        |  driver = "org.postgresql.Driver"
        |}
        |
        |mongo {
        |  host = "localhost"
        |  port = 27017
        |  database = "payment_pipeline"
        |  processedCollection = "processed_transactions"
        |  alertsCollection = "alerts"
        |  violationsCollection = "eligibility_violations"
        |}
        |
        |kafka {
        |  bootstrapServers = "localhost:19092"
        |  topic = "payment-events"
        |  groupId = "payment-event-pipeline"
        |  clientId = "payment-event-pipeline-test"
        |}
        |""".stripMargin
    )

    val appConfig = AppConfig.fromConfig(config)

    assertEquals(appConfig.app.inputMode, InputMode.PacedFile)
    assertEquals(appConfig.app.streamDelay, 250.millis)
  }

  test("loads redpanda input mode") {
    assertEquals(InputMode.fromString("redpanda"), Right(InputMode.Redpanda))
  }

  test("rejects unsupported input mode without throwing raw parser errors") {
    assertEquals(
      InputMode.fromString("broker"),
      Left("Unsupported input mode: broker")
    )
  }

  test("returns explicit error when EMAIL_SALT is missing") {
    val config = ConfigFactory.parseString(validConfig(emailSaltLine = ""))

    val error = AppConfig.fromConfigEither(config).left.toOption.get

    assert(error.getMessage.contains("EMAIL_SALT is required"))
  }

  test("rejects negative stream delay") {
    val config = ConfigFactory.parseString(validConfig(streamDelayMillis = -1))

    val error = AppConfig.fromConfigEither(config).left.toOption.get

    assert(error.getMessage.contains("app.streamDelayMillis must be non-negative"))
  }

  test("rejects out-of-range ports") {
    val config = ConfigFactory.parseString(validConfig(postgresPort = 0, mongoPort = 70000))

    val error = AppConfig.fromConfigEither(config).left.toOption.get

    assert(error.getMessage.contains("postgres.port must be between 1 and 65535"))
    assert(error.getMessage.contains("mongo.port must be between 1 and 65535"))
  }

  private def validConfig(
      emailSaltLine: String = "emailSalt = \"test-salt\"",
      streamDelayMillis: Long = 0,
      postgresPort: Int = 5432,
      mongoPort: Int = 27017
  ): String =
    s"""
       |app {
       |  inputFile = "sample-data/events.jsonl"
       |  outputDir = "out"
       |  $emailSaltLine
       |  inputMode = "file"
       |  streamDelayMillis = $streamDelayMillis
       |}
       |
       |postgres {
       |  host = "localhost"
       |  port = $postgresPort
       |  database = "payment_pipeline"
       |  user = "pipeline_user"
       |  password = "pipeline_pass_dev"
       |  driver = "org.postgresql.Driver"
       |}
       |
       |mongo {
       |  host = "localhost"
       |  port = $mongoPort
       |  database = "payment_pipeline"
       |  processedCollection = "processed_transactions"
       |  alertsCollection = "alerts"
       |  violationsCollection = "eligibility_violations"
       |}
       |
       |kafka {
       |  bootstrapServers = "localhost:19092"
       |  topic = "payment-events"
       |  groupId = "payment-event-pipeline"
       |  clientId = "payment-event-pipeline-test"
       |}
       |""".stripMargin
