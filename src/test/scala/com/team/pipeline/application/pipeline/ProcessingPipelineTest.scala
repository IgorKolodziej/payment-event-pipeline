package com.team.pipeline.application.pipeline

import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all.*
import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.application.risk.RiskPolicy
import com.team.pipeline.application.validation.EmailHasher
import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.AlertType
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.EligibilityViolation
import com.team.pipeline.domain.EligibilityViolationType
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.ProcessedEvent
import com.team.pipeline.domain.RiskDecision
import com.team.pipeline.infrastructure.file.JsonlInput
import com.team.pipeline.ports.AlertStore
import com.team.pipeline.ports.CustomerProfileLookup
import com.team.pipeline.ports.EligibilityViolationStore
import com.team.pipeline.ports.ProcessedEventStore
import com.team.pipeline.ports.RiskFeatureProvider
import fs2.Stream
import munit.CatsEffectSuite

import java.time.Instant

class ProcessingPipelineTest extends CatsEffectSuite:
  test("run rejects malformed JSON before lookup or storage") {
    for
      fixture <- TestFixture.create(customers = Map(customer.customerId -> customer))
      summary <- ProcessingPipeline.run(
        Stream.emit(JsonlInput.Line(1, "not-json")),
        fixture.dependencies
      )
      lookupCalls <- fixture.lookupCalls.get
      contextCalls <- fixture.contextCalls.get
      savedProcessed <- fixture.savedProcessed.get
      savedViolations <- fixture.savedViolations.get
      savedAlerts <- fixture.savedAlerts.get
    yield
      assertEquals(summary.totalRead, 1)
      assertEquals(summary.totalProcessed, 0)
      assertEquals(summary.totalRejected, 1)
      assertEquals(summary.errorCounts, Map("InvalidJson" -> 1))
      assertEquals(lookupCalls, Vector.empty)
      assertEquals(contextCalls, Vector.empty)
      assertEquals(savedProcessed, Vector.empty)
      assertEquals(savedViolations, Vector.empty)
      assertEquals(savedAlerts, Vector.empty)
  }

  test("run rejects validation failures before customer lookup") {
    for
      fixture <- TestFixture.create(customers = Map(customer.customerId -> customer))
      summary <- ProcessingPipeline.run(
        Stream.emit(JsonlInput.Line(1, validLine(amount = "0.00"))),
        fixture.dependencies
      )
      lookupCalls <- fixture.lookupCalls.get
      savedProcessed <- fixture.savedProcessed.get
    yield
      assertEquals(summary.totalRead, 1)
      assertEquals(summary.totalProcessed, 0)
      assertEquals(summary.totalRejected, 1)
      assertEquals(summary.errorCounts, Map("InvalidAmount" -> 1))
      assertEquals(lookupCalls, Vector.empty)
      assertEquals(savedProcessed, Vector.empty)
  }

  test("run rejects missing customers before risk evaluation or storage") {
    for
      fixture <- TestFixture.create(customers = Map.empty)
      summary <- ProcessingPipeline.run(
        Stream.emit(JsonlInput.Line(7, validLine(customerId = 999))),
        fixture.dependencies
      )
      lookupCalls <- fixture.lookupCalls.get
      contextCalls <- fixture.contextCalls.get
      savedProcessed <- fixture.savedProcessed.get
      savedAlerts <- fixture.savedAlerts.get
    yield
      assertEquals(summary.totalRead, 1)
      assertEquals(summary.totalProcessed, 0)
      assertEquals(summary.totalRejected, 1)
      assertEquals(summary.errorCounts, Map("CustomerNotFound" -> 1))
      assertEquals(lookupCalls, Vector(999))
      assertEquals(contextCalls, Vector.empty)
      assertEquals(savedProcessed, Vector.empty)
      assertEquals(savedAlerts, Vector.empty)
  }

  test("run stores and summarizes a processed event with a risk alert") {
    val flaggedCustomer = customer.copy(fraudBefore = true)

    for
      fixture <- TestFixture.create(customers = Map(flaggedCustomer.customerId -> flaggedCustomer))
      summary <- ProcessingPipeline.run(
        Stream.emit(JsonlInput.Line(1, validLine())),
        fixture.dependencies
      )
      lookupCalls <- fixture.lookupCalls.get
      contextCalls <- fixture.contextCalls.get
      savedProcessed <- fixture.savedProcessed.get
      savedViolations <- fixture.savedViolations.get
      savedAlerts <- fixture.savedAlerts.get
      order <- fixture.order.get
    yield
      assertEquals(summary.totalRead, 1)
      assertEquals(summary.totalProcessed, 1)
      assertEquals(summary.totalRejected, 0)
      assertEquals(summary.totalAlerts, 1)
      assertEquals(summary.decisionCounts, Map("Review" -> 1))
      assertEquals(summary.alertCounts, Map("PreviouslyFlaggedCustomer" -> 1))
      assertEquals(summary.countryCounts, Map("PL" -> 1))
      assertEquals(lookupCalls, Vector(customer.customerId))
      assertEquals(contextCalls, Vector(100))
      assertEquals(savedProcessed.size, 1)
      assertEquals(savedProcessed.head.riskScore, 20)
      assertEquals(savedProcessed.head.riskDecision, RiskDecision.Review)
      assertEquals(savedProcessed.head.finalDecision, FinalDecision.Review)
      assertEquals(savedViolations, Vector.empty)
      assertEquals(savedAlerts.map(_.alertType), Vector(AlertType.PreviouslyFlaggedCustomer))
      assert(order.indexOf("context:100") < order.indexOf("processed:100"))
  }

  test("run stores eligibility violations and skips risk output for declined events") {
    val inactiveCustomer = customer.copy(isActive = false)

    for
      fixture <-
        TestFixture.create(customers = Map(inactiveCustomer.customerId -> inactiveCustomer))
      summary <- ProcessingPipeline.run(
        Stream.emit(JsonlInput.Line(1, validLine())),
        fixture.dependencies
      )
      contextCalls <- fixture.contextCalls.get
      savedProcessed <- fixture.savedProcessed.get
      savedViolations <- fixture.savedViolations.get
      savedAlerts <- fixture.savedAlerts.get
      order <- fixture.order.get
    yield
      assertEquals(summary.totalRead, 1)
      assertEquals(summary.totalProcessed, 1)
      assertEquals(summary.totalRejected, 0)
      assertEquals(summary.totalAlerts, 0)
      assertEquals(summary.decisionCounts, Map("Declined" -> 1))
      assertEquals(contextCalls, Vector(100))
      assertEquals(savedProcessed.size, 1)
      assertEquals(savedProcessed.head.riskScore, 0)
      assertEquals(savedProcessed.head.riskDecision, RiskDecision.NotEvaluated)
      assertEquals(savedProcessed.head.finalDecision, FinalDecision.Declined)
      assertEquals(
        savedViolations.map(_.violationType),
        Vector(EligibilityViolationType.InactiveCustomer)
      )
      assertEquals(savedAlerts, Vector.empty)
      assert(order.indexOf("context:100") < order.indexOf("processed:100"))
  }

  private final case class TestFixture(
      dependencies: ProcessingPipeline.Dependencies,
      lookupCalls: Ref[IO, Vector[Int]],
      contextCalls: Ref[IO, Vector[Int]],
      savedProcessed: Ref[IO, Vector[ProcessedEvent]],
      savedViolations: Ref[IO, Vector[EligibilityViolation]],
      savedAlerts: Ref[IO, Vector[Alert]],
      order: Ref[IO, Vector[String]]
  )

  private object TestFixture:
    def create(
        customers: Map[Int, CustomerProfile],
        context: CustomerRiskContext = emptyContext
    ): IO[TestFixture] =
      for
        lookupCalls <- Ref[IO].of(Vector.empty[Int])
        contextCalls <- Ref[IO].of(Vector.empty[Int])
        savedProcessed <- Ref[IO].of(Vector.empty[ProcessedEvent])
        savedViolations <- Ref[IO].of(Vector.empty[EligibilityViolation])
        savedAlerts <- Ref[IO].of(Vector.empty[Alert])
        order <- Ref[IO].of(Vector.empty[String])
      yield
        val customerLookup = new CustomerProfileLookup:
          override def find(customerId: Int): IO[Option[CustomerProfile]] =
            lookupCalls.update(_ :+ customerId).as(customers.get(customerId))

        val riskFeatureProvider = new RiskFeatureProvider:
          override def contextFor(event: EnrichedPaymentEvent): IO[CustomerRiskContext] =
            order.update(_ :+ s"context:${event.event.eventId}") *>
              contextCalls.update(_ :+ event.event.eventId).as(context)

        val processedEventStore = new ProcessedEventStore:
          override def save(event: ProcessedEvent): IO[Unit] =
            order.update(_ :+ s"processed:${event.eventId}") *>
              savedProcessed.update(_ :+ event)

        val eligibilityViolationStore = new EligibilityViolationStore:
          override def saveAll(violations: List[EligibilityViolation]): IO[Unit] =
            if violations.isEmpty then IO.unit
            else
              order.update(_ :+ "violations") *>
                savedViolations.update(_ ++ violations)

        val alertStore = new AlertStore:
          override def saveAll(alerts: List[Alert]): IO[Unit] =
            if alerts.isEmpty then IO.unit
            else
              order.update(_ :+ "alerts") *>
                savedAlerts.update(_ ++ alerts)

        val dependencies = ProcessingPipeline.Dependencies(
          customerLookup = customerLookup,
          riskFeatureProvider = riskFeatureProvider,
          processedEventStore = processedEventStore,
          eligibilityViolationStore = eligibilityViolationStore,
          alertStore = alertStore,
          emailHasher = EmailHasher.sha256("test-salt"),
          riskPolicy = RiskPolicy.default
        )

        TestFixture(
          dependencies = dependencies,
          lookupCalls = lookupCalls,
          contextCalls = contextCalls,
          savedProcessed = savedProcessed,
          savedViolations = savedViolations,
          savedAlerts = savedAlerts,
          order = order
        )

  private val customer = CustomerProfile(
    customerId = 10,
    firstName = "Beata",
    lastName = "Krolak",
    email = "b.krolak@firma.pl",
    country = "PL",
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

  private def validLine(
      eventId: Int = 100,
      customerId: Int = customer.customerId,
      amount: String = "150.00",
      status: String = "SUCCESS",
      paymentMethod: String = "BLIK",
      transactionCountry: String = "PL"
  ): String =
    s"""{"eventId":$eventId,"timestamp":"2026-04-24T10:00:00Z","customerId":$customerId,"amount":$amount,"currency":"PLN","status":"$status","paymentMethod":"$paymentMethod","transactionCountry":"$transactionCountry","merchantId":"M001","merchantCategory":"GROCERY","channel":"MOBILE","deviceId":"device-001"}"""
end ProcessingPipelineTest
