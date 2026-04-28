package com.team.pipeline

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import com.team.pipeline.application.pipeline.ProcessingPipeline
import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.application.risk.RiskPolicy
import com.team.pipeline.application.validation.EmailHasher
import com.team.pipeline.config.AppConfig
import com.team.pipeline.config.AppSettings
import com.team.pipeline.config.InputMode
import com.team.pipeline.config.KafkaConfig
import com.team.pipeline.config.MongoConfig
import com.team.pipeline.config.PostgresConfig
import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.EligibilityViolation
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.ProcessedEvent
import com.team.pipeline.infrastructure.file.FileReplayEventSource
import com.team.pipeline.infrastructure.file.PacedFileReplayEventSource
import com.team.pipeline.infrastructure.redpanda.RedpandaEventSource
import com.team.pipeline.ports.AlertStore
import com.team.pipeline.ports.CustomerProfileLookup
import com.team.pipeline.ports.EligibilityViolationStore
import com.team.pipeline.ports.ProcessedEventStore
import com.team.pipeline.ports.RiskFeatureProvider
import munit.CatsEffectSuite

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import scala.concurrent.duration.DurationInt

class MainTest extends CatsEffectSuite:
  test("runWith reads configured JSONL input and returns summary") {
    tempDirectory.use { tempDir =>
      val inputFile = tempDir.resolve("events.jsonl")
      val outputDir = tempDir.resolve("out")

      for
        _ <- IO.blocking(Files.writeString(inputFile, validLine))
        savedProcessed <- Ref[IO].of(Vector.empty[ProcessedEvent])
        summary <- Main.runWith(
          testConfig(inputFile, outputDir),
          testDependencies(savedProcessed),
          FileReplayEventSource(inputFile)
        )
        outputExists <- IO.blocking(Files.isDirectory(outputDir))
        processed <- savedProcessed.get
      yield
        assert(outputExists)
        assertEquals(summary.totalRead, 1)
        assertEquals(summary.totalProcessed, 1)
        assertEquals(summary.totalRejected, 0)
        assertEquals(summary.decisionCounts, Map("Accepted" -> 1))
        assertEquals(processed.size, 1)
        assertEquals(processed.head.finalDecision, FinalDecision.Accepted)
    }
  }

  test("eventSource selects file replay source by default") {
    tempDirectory.use { tempDir =>
      val config = testConfig(tempDir.resolve("events.jsonl"), tempDir.resolve("out"))

      IO(assert(Main.eventSource(config).isInstanceOf[FileReplayEventSource]))
    }
  }

  test("eventSource selects paced file replay source") {
    tempDirectory.use { tempDir =>
      val baseConfig = testConfig(tempDir.resolve("events.jsonl"), tempDir.resolve("out"))
      val config = baseConfig.copy(
        app = baseConfig.app.copy(
          inputMode = InputMode.PacedFile,
          streamDelay = 250.millis
        )
      )

      IO(assert(Main.eventSource(config).isInstanceOf[PacedFileReplayEventSource]))
    }
  }

  test("eventSource selects Redpanda source") {
    tempDirectory.use { tempDir =>
      val baseConfig = testConfig(tempDir.resolve("events.jsonl"), tempDir.resolve("out"))
      val config = baseConfig.copy(app = baseConfig.app.copy(inputMode = InputMode.Redpanda))

      IO(assert(Main.eventSource(config).isInstanceOf[RedpandaEventSource]))
    }
  }

  private def testDependencies(
      savedProcessed: Ref[IO, Vector[ProcessedEvent]]
  ): ProcessingPipeline.Dependencies =
    val customerLookup = new CustomerProfileLookup:
      override def find(customerId: Int): IO[Option[CustomerProfile]] =
        IO.pure(Some(customer))

    val riskFeatureProvider = new RiskFeatureProvider:
      override def contextFor(event: EnrichedPaymentEvent): IO[CustomerRiskContext] =
        IO.pure(emptyContext)

    val processedEventStore = new ProcessedEventStore:
      override def save(event: ProcessedEvent): IO[Unit] =
        savedProcessed.update(_ :+ event)

    val eligibilityViolationStore = new EligibilityViolationStore:
      override def saveAll(violations: List[EligibilityViolation]): IO[Unit] =
        IO.unit

    val alertStore = new AlertStore:
      override def saveAll(alerts: List[Alert]): IO[Unit] =
        IO.unit

    ProcessingPipeline.Dependencies(
      customerLookup = customerLookup,
      riskFeatureProvider = riskFeatureProvider,
      processedEventStore = processedEventStore,
      eligibilityViolationStore = eligibilityViolationStore,
      alertStore = alertStore,
      emailHasher = EmailHasher.sha256("test-salt"),
      riskPolicy = RiskPolicy.default
    )

  private def testConfig(inputFile: Path, outputDir: Path): AppConfig =
    AppConfig(
      app = AppSettings(
        inputFile = inputFile,
        outputDir = outputDir,
        emailSalt = "test-salt",
        inputMode = InputMode.File,
        streamDelay = 0.millis
      ),
      postgres = PostgresConfig(
        host = "localhost",
        port = 5432,
        database = "payment_pipeline",
        user = "pipeline_user",
        password = "secret",
        driver = "org.postgresql.Driver"
      ),
      mongo = MongoConfig(
        host = "localhost",
        port = 27017,
        database = "payment_pipeline",
        processedCollection = "processed_transactions",
        alertsCollection = "alerts",
        violationsCollection = "eligibility_violations"
      ),
      kafka = KafkaConfig(
        bootstrapServers = "localhost:19092",
        topic = "payment-events",
        groupId = "payment-event-pipeline",
        clientId = "payment-event-pipeline-test"
      )
    )

  private val customer = CustomerProfile(
    customerId = 10,
    firstName = "Beata",
    lastName = "Krolak",
    email = "b.krolak@firma.pl",
    country = "PL",
    accountCurrency = Currency.PLN,
    balance = BigDecimal("5500.00"),
    dailyLimit = BigDecimal("5000.00"),
    allowedPaymentMethods = Set(PaymentMethod.Blik, PaymentMethod.Transfer),
    isActive = true,
    age = 38,
    gender = "F",
    lastLoginCountry = "PL",
    fraudBefore = false,
    createdAt = Instant.parse("2021-06-18T10:00:00Z")
  )

  private val emptyContext = CustomerRiskContext(
    transactionCountLastHour = 0,
    failedAttemptCountLastHour = 0,
    approvedAmountLast24h = BigDecimal("0"),
    lateNightTransactionCountLast7d = 0,
    knownDevice = true,
    averageAmount30d = None,
    amountStddev30d = None,
    historySize30d = 0,
    blikTransferCountLast24h = 0,
    blikTransferCountLast30d = 0,
    totalTransactionCountLast30d = 0
  )

  private val validLine =
    """{"eventId":100,"timestamp":"2026-04-24T10:00:00Z","customerId":10,"amount":150.00,"currency":"PLN","status":"SUCCESS","paymentMethod":"BLIK","transactionCountry":"PL","merchantId":"M001","merchantCategory":"GROCERY","channel":"MOBILE","deviceId":"device-001"}"""

  private def tempDirectory: Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempDirectory("payment-pipeline-main-")))(
      deleteRecursively
    )

  private def deleteRecursively(path: Path): IO[Unit] =
    IO.blocking {
      if Files.exists(path) then
        val paths = Files.walk(path)
        try
          paths
            .sorted(java.util.Comparator.reverseOrder())
            .forEach((p: Path) => {
              Files.deleteIfExists(p)
              ()
            })
        finally paths.close()
    }
end MainTest
