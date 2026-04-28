package com.team.pipeline

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import com.mongodb.client.MongoClients
import com.team.pipeline.application.pipeline.ProcessingPipeline
import com.team.pipeline.application.reporting.RunSummary
import com.team.pipeline.application.risk.RiskPolicy
import com.team.pipeline.application.validation.EmailHasher
import com.team.pipeline.config.AppConfig
import com.team.pipeline.config.InputMode
import com.team.pipeline.infrastructure.file.FileReplayEventSource
import com.team.pipeline.infrastructure.file.PacedFileReplayEventSource
import com.team.pipeline.infrastructure.mongo.MongoAlertStore
import com.team.pipeline.infrastructure.mongo.MongoEligibilityViolationStore
import com.team.pipeline.infrastructure.mongo.MongoIndexInitializer
import com.team.pipeline.infrastructure.mongo.MongoProcessedEventStore
import com.team.pipeline.infrastructure.mongo.MongoRiskFeatureProvider
import com.team.pipeline.infrastructure.postgres.DoobieCustomerProfileLookup
import com.team.pipeline.infrastructure.redpanda.RedpandaEventSource
import com.team.pipeline.ports.EventSource
import doobie.hikari.{Config as HikariConfig, HikariTransactor}

import java.nio.file.Files

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    for
      config <- AppConfig.load
      _ <- IO.println(
        s"Payment Event Processing Pipeline started. input=${config.app.inputFile}, output=${config.app.outputDir}"
      )
      source = eventSource(config)
      summary <- liveDependencies(config).use(runWith(config, _, source))
      _ <- IO.println(
        s"Payment Event Processing Pipeline finished. read=${summary.totalRead}, processed=${summary.totalProcessed}, rejected=${summary.totalRejected}, alerts=${summary.totalAlerts}"
      )
    yield ()

  private[pipeline] def runWith(
      config: AppConfig,
      dependencies: ProcessingPipeline.Dependencies,
      eventSource: EventSource
  ): IO[RunSummary] =
    for
      _ <- IO.blocking(Files.createDirectories(config.app.outputDir))
      summary <- ProcessingPipeline.run(eventSource.events, dependencies)
    yield summary

  private[pipeline] def eventSource(config: AppConfig): EventSource =
    config.app.inputMode match
      case InputMode.File =>
        FileReplayEventSource(config.app.inputFile)
      case InputMode.PacedFile =>
        PacedFileReplayEventSource(config.app.inputFile, config.app.streamDelay)
      case InputMode.Redpanda =>
        RedpandaEventSource(config.kafka)

  private def liveDependencies(
      config: AppConfig
  ): Resource[IO, ProcessingPipeline.Dependencies] =
    val riskPolicy = RiskPolicy.default

    for
      transactor <- HikariTransactor.fromConfig[IO](
        postgresHikariConfig(config),
        logHandler = None
      )
      mongoClient <- Resource.make(IO.blocking(MongoClients.create(mongoUrl(config))))(client =>
        IO.blocking(client.close())
      )
      dependencies <- Resource.eval {
        val database = mongoClient.getDatabase(config.mongo.database)
        val processedCollection = database.getCollection(config.mongo.processedCollection)
        val alertsCollection = database.getCollection(config.mongo.alertsCollection)
        val violationsCollection = database.getCollection(config.mongo.violationsCollection)

        MongoIndexInitializer.initialize(database, config.mongo).as(
          ProcessingPipeline.Dependencies(
            customerLookup = DoobieCustomerProfileLookup(transactor),
            riskFeatureProvider = MongoRiskFeatureProvider(processedCollection, riskPolicy),
            processedEventStore = MongoProcessedEventStore(processedCollection),
            eligibilityViolationStore = MongoEligibilityViolationStore(violationsCollection),
            alertStore = MongoAlertStore(alertsCollection),
            emailHasher = EmailHasher.sha256(config.app.emailSalt),
            riskPolicy = riskPolicy
          )
        )
      }
    yield dependencies

  private def postgresHikariConfig(config: AppConfig): HikariConfig =
    HikariConfig(
      jdbcUrl = jdbcUrl(config),
      username = Some(config.postgres.user),
      password = Some(config.postgres.password),
      driverClassName = Some(config.postgres.driver),
      maximumPoolSize = 4,
      minimumIdle = 1,
      poolName = Some("payment-event-pipeline-postgres")
    )

  private def jdbcUrl(config: AppConfig): String =
    s"jdbc:postgresql://${config.postgres.host}:${config.postgres.port}/${config.postgres.database}"

  private def mongoUrl(config: AppConfig): String =
    s"mongodb://${config.mongo.host}:${config.mongo.port}"
