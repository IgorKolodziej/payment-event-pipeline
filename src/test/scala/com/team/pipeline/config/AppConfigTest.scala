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
        |""".stripMargin
    )

    val appConfig = AppConfig.fromConfig(config)

    assertEquals(appConfig.app.inputMode, InputMode.PacedFile)
    assertEquals(appConfig.app.streamDelay, 250.millis)
  }
