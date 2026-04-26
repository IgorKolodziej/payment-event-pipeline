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
import com.team.pipeline.infrastructure.file.JsonlInput
import com.team.pipeline.infrastructure.mongo.MongoAlertStore
import com.team.pipeline.infrastructure.mongo.MongoEligibilityViolationStore
import com.team.pipeline.infrastructure.mongo.MongoProcessedEventStore
import com.team.pipeline.infrastructure.mongo.MongoRiskFeatureProvider
import com.team.pipeline.infrastructure.postgres.DoobieCustomerProfileLookup
import doobie.Transactor

import java.nio.file.Files

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    for
      config <- AppConfig.load
      _ <- IO.println(
        s"Payment Event Processing Pipeline started. input=${config.app.inputFile}, output=${config.app.outputDir}"
      )
      summary <- liveDependencies(config).use(runWith(config, _))
      _ <- IO.println(
        s"Payment Event Processing Pipeline finished. read=${summary.totalRead}, processed=${summary.totalProcessed}, rejected=${summary.totalRejected}, alerts=${summary.totalAlerts}"
      )
    yield ()

  private[pipeline] def runWith(
      config: AppConfig,
      dependencies: ProcessingPipeline.Dependencies
  ): IO[RunSummary] =
    for
      _ <- IO.blocking(Files.createDirectories(config.app.outputDir))
      summary <- ProcessingPipeline.run(JsonlInput.read(config.app.inputFile), dependencies)
    yield summary

  private def liveDependencies(
      config: AppConfig
  ): Resource[IO, ProcessingPipeline.Dependencies] =
    val riskPolicy = RiskPolicy.default
    val transactor = Transactor.fromDriverManager[IO](
      driver = config.postgres.driver,
      url = jdbcUrl(config),
      user = config.postgres.user,
      password = config.postgres.password,
      logHandler = None
    )

    Resource
      .make(IO.blocking(MongoClients.create(mongoUrl(config))))(client =>
        IO.blocking(client.close())
      )
      .map { mongoClient =>
        val database = mongoClient.getDatabase(config.mongo.database)
        val processedCollection = database.getCollection(config.mongo.processedCollection)
        val alertsCollection = database.getCollection(config.mongo.alertsCollection)
        val violationsCollection = database.getCollection(config.mongo.violationsCollection)

        ProcessingPipeline.Dependencies(
          customerLookup = DoobieCustomerProfileLookup(transactor),
          riskFeatureProvider = MongoRiskFeatureProvider(processedCollection, riskPolicy),
          processedEventStore = MongoProcessedEventStore(processedCollection),
          eligibilityViolationStore = MongoEligibilityViolationStore(violationsCollection),
          alertStore = MongoAlertStore(alertsCollection),
          emailHasher = EmailHasher.sha256(config.app.emailSalt),
          riskPolicy = riskPolicy
        )
      }

  private def jdbcUrl(config: AppConfig): String =
    s"jdbc:postgresql://${config.postgres.host}:${config.postgres.port}/${config.postgres.database}"

  private def mongoUrl(config: AppConfig): String =
    s"mongodb://${config.mongo.host}:${config.mongo.port}"
