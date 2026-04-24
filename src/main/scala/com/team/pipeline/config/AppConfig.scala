package com.team.pipeline.config

import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.Path

final case class AppConfig(
    app: AppSettings,
    postgres: PostgresConfig,
    mongo: MongoConfig
)

final case class AppSettings(
    inputFile: Path,
    outputDir: Path,
    emailSalt: String
)

final case class PostgresConfig(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    driver: String
)

final case class MongoConfig(
    host: String,
    port: Int,
    database: String,
    processedCollection: String,
    alertsCollection: String
)

object AppConfig:
  def load: IO[AppConfig] =
    IO.blocking(ConfigFactory.load().resolve()).map(fromConfig)

  def fromConfig(config: Config): AppConfig =
    AppConfig(
      app = AppSettings(
        inputFile = Path.of(config.getString("app.inputFile")),
        outputDir = Path.of(config.getString("app.outputDir")),
        emailSalt = config.getString("app.emailSalt")
      ),
      postgres = PostgresConfig(
        host = config.getString("postgres.host"),
        port = config.getInt("postgres.port"),
        database = config.getString("postgres.database"),
        user = config.getString("postgres.user"),
        password = config.getString("postgres.password"),
        driver = config.getString("postgres.driver")
      ),
      mongo = MongoConfig(
        host = config.getString("mongo.host"),
        port = config.getInt("mongo.port"),
        database = config.getString("mongo.database"),
        processedCollection = config.getString("mongo.processedCollection"),
        alertsCollection = config.getString("mongo.alertsCollection")
      )
    )
