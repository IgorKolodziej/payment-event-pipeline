package com.team.pipeline.config

import cats.data.NonEmptyChain
import cats.data.ValidatedNec
import cats.effect.IO
import cats.syntax.all.*
import com.typesafe.config.{Config, ConfigFactory}

import java.nio.file.Path
import scala.util.control.NonFatal
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS

final case class AppConfig(
    app: AppSettings,
    postgres: PostgresConfig,
    mongo: MongoConfig,
    kafka: KafkaConfig
)

final case class AppConfigError(messages: NonEmptyChain[String])
    extends IllegalArgumentException(messages.toNonEmptyList.toList.mkString("; "))

final case class AppSettings(
    inputFile: Path,
    outputDir: Path,
    emailSalt: String,
    inputMode: InputMode,
    streamDelay: FiniteDuration
)

enum InputMode:
  case File, PacedFile, Redpanda

object InputMode:
  def fromString(value: String): Either[String, InputMode] =
    value.trim.toLowerCase match
      case "file"       => Right(InputMode.File)
      case "paced-file" => Right(InputMode.PacedFile)
      case "redpanda"   => Right(InputMode.Redpanda)
      case other        => Left(s"Unsupported input mode: $other")

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

final case class KafkaConfig(
    bootstrapServers: String,
    topic: String,
    groupId: String,
    clientId: String
)

object AppConfig:
  def load: IO[AppConfig] =
    IO.blocking(ConfigFactory.load().resolve()).map(fromConfig)

  def fromConfig(config: Config): AppConfig =
    fromConfigEither(config).fold(error => throw error, identity)

  def fromConfigEither(config: Config): Either[AppConfigError, AppConfig] =
    val appSettings =
      (
        path(config, "app.inputFile", "app.inputFile"),
        path(config, "app.outputDir", "app.outputDir"),
        nonEmptyString(config, "app.emailSalt", "EMAIL_SALT"),
        inputMode(config),
        nonNegativeLong(config, "app.streamDelayMillis", "app.streamDelayMillis")
      ).mapN { (inputFile, outputDir, emailSalt, inputMode, streamDelayMillis) =>
        AppSettings(
          inputFile = inputFile,
          outputDir = outputDir,
          emailSalt = emailSalt,
          inputMode = inputMode,
          streamDelay = FiniteDuration(streamDelayMillis, MILLISECONDS)
        )
      }

    val postgres =
      (
        nonEmptyString(config, "postgres.host", "postgres.host"),
        port(config, "postgres.port", "postgres.port"),
        nonEmptyString(config, "postgres.database", "postgres.database"),
        nonEmptyString(config, "postgres.user", "postgres.user"),
        nonEmptyString(config, "postgres.password", "postgres.password"),
        nonEmptyString(config, "postgres.driver", "postgres.driver")
      ).mapN(PostgresConfig.apply)

    val mongo =
      (
        nonEmptyString(config, "mongo.host", "mongo.host"),
        port(config, "mongo.port", "mongo.port"),
        nonEmptyString(config, "mongo.database", "mongo.database"),
        nonEmptyString(config, "mongo.processedCollection", "mongo.processedCollection"),
        nonEmptyString(config, "mongo.alertsCollection", "mongo.alertsCollection"),
        nonEmptyString(config, "mongo.violationsCollection", "mongo.violationsCollection")
      ).mapN(MongoConfig.apply)

    val kafka =
      (
        nonEmptyString(config, "kafka.bootstrapServers", "kafka.bootstrapServers"),
        nonEmptyString(config, "kafka.topic", "kafka.topic"),
        nonEmptyString(config, "kafka.groupId", "kafka.groupId"),
        nonEmptyString(config, "kafka.clientId", "kafka.clientId")
      ).mapN(KafkaConfig.apply)

    (appSettings, postgres, mongo, kafka)
      .mapN(AppConfig.apply)
      .toEither
      .leftMap(AppConfigError.apply)

  private def path(
      config: Config,
      path: String,
      label: String
  ): ValidatedNec[String, Path] =
    nonEmptyString(config, path, label)
      .andThen(value =>
        Either
          .catchNonFatal(Path.of(value))
          .leftMap(_ => s"$label is not a valid path")
          .toValidatedNec
      )

  private def inputMode(config: Config): ValidatedNec[String, InputMode] =
    nonEmptyString(config, "app.inputMode", "app.inputMode")
      .andThen(value => InputMode.fromString(value).toValidatedNec)

  private def nonEmptyString(
      config: Config,
      path: String,
      label: String
  ): ValidatedNec[String, String] =
    read(config, path, label)(_.getString(path))
      .andThen { value =>
        val trimmed = value.trim
        if trimmed.nonEmpty then trimmed.validNec
        else s"$label must be non-empty".invalidNec
      }

  private def port(
      config: Config,
      path: String,
      label: String
  ): ValidatedNec[String, Int] =
    read(config, path, label)(_.getInt(path))
      .andThen { value =>
        if value > 0 && value <= 65535 then value.validNec
        else s"$label must be between 1 and 65535".invalidNec
      }

  private def nonNegativeLong(
      config: Config,
      path: String,
      label: String
  ): ValidatedNec[String, Long] =
    read(config, path, label)(_.getLong(path))
      .andThen { value =>
        if value >= 0 then value.validNec
        else s"$label must be non-negative".invalidNec
      }

  private def read[A](
      config: Config,
      path: String,
      label: String
  )(readValue: Config => A): ValidatedNec[String, A] =
    if !config.hasPath(path) then s"$label is required".invalidNec
    else
      try readValue(config).validNec
      catch
        case NonFatal(error) =>
          s"$label is invalid: ${error.getMessage}".invalidNec
