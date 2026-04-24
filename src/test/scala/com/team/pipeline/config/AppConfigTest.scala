package com.team.pipeline.config

import com.typesafe.config.ConfigFactory
import munit.FunSuite

import java.nio.file.Path

class AppConfigTest extends FunSuite:
  test("loads typed app config from HOCON") {
    val config = ConfigFactory.parseString(
      """
        |app {
        |  inputFile = "sample-data/events.jsonl"
        |  outputDir = "out"
        |  emailSalt = "test-salt"
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
        |}
        |""".stripMargin
    )

    val appConfig = AppConfig.fromConfig(config)

    assertEquals(appConfig.app.inputFile, Path.of("sample-data/events.jsonl"))
    assertEquals(appConfig.app.outputDir, Path.of("out"))
    assertEquals(appConfig.app.emailSalt, "test-salt")
    assertEquals(appConfig.postgres.port, 5432)
    assertEquals(appConfig.postgres.database, "payment_pipeline")
    assertEquals(appConfig.mongo.port, 27017)
    assertEquals(appConfig.mongo.processedCollection, "processed_transactions")
    assertEquals(appConfig.mongo.alertsCollection, "alerts")
  }
