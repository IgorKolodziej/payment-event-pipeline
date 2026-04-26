package com.team.pipeline.config

import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS

final case class AppConfig(
    app: AppSettings,
    postgres: PostgresConfig,
    mongo: MongoConfig
)

final case class AppSettings(
    inputFile: Path,
    outputDir: Path,
    emailSalt: String,
    inputMode: InputMode,
    streamDelay: FiniteDuration
)

enum InputMode:
  case File

object InputMode:
  def fromString(value: String): InputMode =
    value.trim.toLowerCase match
      case "file" => InputMode.File
      case other  => throw IllegalArgumentException(s"Unsupported input mode: $other")

final case class PostgresConfig(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    driver: String
):
  override def toString: String =
    s"PostgresConfig(host=$host, port=$port, database=$database, user=$user, " +
      s"password=****, driver=$driver)"

final case class MongoConfig(
    host: String,
    port: Int,
    database: String,
    processedCollection: String,
    alertsCollection: String,
    violationsCollection: String
)

object AppConfig:
  def load: IO[AppConfig] =
    IO.blocking(ConfigFactory.load().resolve()).map(fromConfig)

  def fromConfig(config: Config): AppConfig =
    val app = config.getConfig("app")
    val postgres = config.getConfig("postgres")
    val mongo = config.getConfig("mongo")

    AppConfig(
      app = AppSettings(
        inputFile = Path.of(app.getString("inputFile")),
        outputDir = Path.of(app.getString("outputDir")),
        emailSalt = app.getString("emailSalt"),
        inputMode = InputMode.fromString(app.getString("inputMode")),
        streamDelay = FiniteDuration(app.getLong("streamDelayMillis"), MILLISECONDS)
      ),
      postgres = PostgresConfig(
        host = postgres.getString("host"),
        port = postgres.getInt("port"),
        database = postgres.getString("database"),
        user = postgres.getString("user"),
        password = postgres.getString("password"),
        driver = postgres.getString("driver")
      ),
      mongo = MongoConfig(
        host = mongo.getString("host"),
        port = mongo.getInt("port"),
        database = mongo.getString("database"),
        processedCollection = mongo.getString("processedCollection"),
        alertsCollection = mongo.getString("alertsCollection"),
        violationsCollection = mongo.getString("violationsCollection")
      )
    )
