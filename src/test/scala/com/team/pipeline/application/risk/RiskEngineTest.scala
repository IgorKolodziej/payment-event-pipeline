package com.team.pipeline.application.risk

import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.AlertType
import com.team.pipeline.domain.RiskAssessment
import com.team.pipeline.domain.RiskDecision
import munit.FunSuite

import java.time.Instant

class RiskEngineTest extends FunSuite:
  private val event = NormalizedPaymentEvent(
    eventId = 100,
    timestamp = Instant.parse("2026-04-24T10:00:00Z"),
    customerId = 10,
    amount = BigDecimal("150.00"),
    currency = Currency.PLN,
    status = EventStatus.Success,
    paymentMethod = PaymentMethod.Blik,
    transactionCountry = "PL",
    merchantId = "M001",
    merchantCategory = MerchantCategory.Grocery,
    channel = PaymentChannel.Mobile,
    deviceId = "device-001"
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

  private val enriched = EnrichedPaymentEvent(
    event = event,
    customer = customer,
    hashedCustomerEmail = "hashed-email"
  )

  private val context = CustomerRiskContext(
    transactionCountLastHour = 0,
    failedAttemptCountLastHour = 0,
    approvedAmountLast24h = BigDecimal("0"),
    lateNightTransactionCountLast7d = 0,
    knownDevice = true,
    firstSeenDeviceAt = Some(Instant.parse("2025-01-01T00:00:00Z")),
    averageAmount30d = None,
    amountStddev30d = None,
    historySize30d = 0
  )

  private def assess(
      enrichedEvent: EnrichedPaymentEvent
  ): RiskAssessment =
    RiskEngine.evaluate(enrichedEvent, context, RiskPolicy.default)

  private def enrichedWith(
      normalizedEvent: NormalizedPaymentEvent = event,
      profile: CustomerProfile = customer
  ): EnrichedPaymentEvent =
    enriched.copy(event = normalizedEvent, customer = profile)

  test("evaluate returns approve assessment when no rules fire") {
    val assessment = RiskEngine.evaluate(enriched, context, RiskPolicy.default)

    assertEquals(
      assessment,
      RiskAssessment(
        riskScore = 0,
        decision = RiskDecision.Approve,
        alerts = Nil
      )
    )
  }

  test("decisionForScore uses review and block thresholds") {
    val policy = RiskPolicy.default

    assertEquals(
      RiskEngine.decisionForScore(policy.reviewThreshold - 1, policy),
      RiskDecision.Approve
    )
    assertEquals(
      RiskEngine.decisionForScore(policy.reviewThreshold, policy),
      RiskDecision.Review
    )
    assertEquals(
      RiskEngine.decisionForScore(policy.blockThreshold - 1, policy),
      RiskDecision.Review
    )
    assertEquals(
      RiskEngine.decisionForScore(policy.blockThreshold, policy),
      RiskDecision.Block
    )
  }

  test("evaluate flags inactive customer") {
    val assessment = assess(enrichedWith(profile = customer.copy(isActive = false)))

    assertSingleAlert(
      assessment,
      alertType = AlertType.InactiveCustomer,
      riskScore = 50,
      decision = RiskDecision.Review
    )
  }

  test("evaluate flags amount above daily limit") {
    val assessment =
      assess(enrichedWith(normalizedEvent = event.copy(amount = BigDecimal("5000.01"))))

    assertSingleAlert(
      assessment,
      alertType = AlertType.LimitExceeded,
      riskScore = 30,
      decision = RiskDecision.Approve
    )
  }

  test("evaluate flags disabled payment method") {
    val assessment =
      assess(enrichedWith(normalizedEvent = event.copy(paymentMethod = PaymentMethod.Card)))

    assertSingleAlert(
      assessment,
      alertType = AlertType.InvalidPaymentMethod,
      riskScore = 25,
      decision = RiskDecision.Approve
    )
  }

  test("evaluate flags customer with previous fraud history") {
    val assessment = assess(enrichedWith(profile = customer.copy(fraudBefore = true)))

    assertSingleAlert(
      assessment,
      alertType = AlertType.PreviouslyFlaggedCustomer,
      riskScore = 20,
      decision = RiskDecision.Approve
    )
  }

  test("evaluate flags country outside known customer countries") {
    val assessment = assess(enrichedWith(normalizedEvent = event.copy(transactionCountry = "US")))

    assertSingleAlert(
      assessment,
      alertType = AlertType.CountryMismatch,
      riskScore = 15,
      decision = RiskDecision.Approve
    )
  }

  test("evaluate accepts country matching last login country") {
    val profile = customer.copy(country = "PL", lastLoginCountry = "DE")
    val normalizedEvent = event.copy(transactionCountry = "DE")

    assertEquals(assess(enrichedWith(normalizedEvent, profile)).alerts, Nil)
  }

  test("evaluate flags high amount from new account") {
    val profile = customer.copy(
      createdAt = event.timestamp.minusSeconds(24L * 60L * 60L)
    )
    val normalizedEvent = event.copy(amount = BigDecimal("4500.00"))

    assertSingleAlert(
      assess(enrichedWith(normalizedEvent, profile)),
      alertType = AlertType.NewAccountHighAmount,
      riskScore = 20,
      decision = RiskDecision.Approve
    )
  }

  test("evaluate flags late-night transaction") {
    val normalizedEvent =
      event.copy(timestamp = Instant.parse("2026-04-24T02:30:00Z"))

    assertSingleAlert(
      assess(enrichedWith(normalizedEvent = normalizedEvent)),
      alertType = AlertType.LateNightTransaction,
      riskScore = 10,
      decision = RiskDecision.Approve
    )
  }

  private def assertSingleAlert(
      assessment: RiskAssessment,
      alertType: AlertType,
      riskScore: Int,
      decision: RiskDecision
  ): Unit =
    assertEquals(assessment.riskScore, riskScore)
    assertEquals(assessment.decision, decision)
    assertEquals(assessment.alerts.map(_.alertType), List(alertType))
    assertEquals(assessment.alerts.map(_.riskScore), List(riskScore))
end RiskEngineTest
