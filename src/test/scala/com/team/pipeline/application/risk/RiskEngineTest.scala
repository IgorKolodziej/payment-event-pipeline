package com.team.pipeline.application.risk

import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.EventId
import com.team.pipeline.domain.AlertType
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.RiskAssessment
import com.team.pipeline.domain.RiskDecision
import munit.FunSuite

import java.time.Instant

class RiskEngineTest extends FunSuite:
  private val event = NormalizedPaymentEvent(
    eventId = EventId(100),
    timestamp = Instant.parse("2026-04-24T10:00:00Z"),
    customerId = CustomerId(10),
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
    customerId = CustomerId(10),
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
    averageAmount30d = None,
    amountStddev30d = None,
    historySize30d = 0,
    blikTransferCountLast24h = 0,
    blikTransferCountLast30d = 0,
    totalTransactionCountLast30d = 0
  )

  private def assess(
      enrichedEvent: EnrichedPaymentEvent,
      riskContext: CustomerRiskContext = context,
      policy: RiskPolicy = RiskPolicy.default
  ): RiskAssessment =
    RiskEngine.evaluate(enrichedEvent, riskContext, policy)

  private def enrichedWith(
      normalizedEvent: NormalizedPaymentEvent = event,
      profile: CustomerProfile = customer
  ): EnrichedPaymentEvent =
    enriched.copy(event = normalizedEvent, customer = profile)

  test("evaluate returns approve assessment when no fraud rules fire") {
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
    given RiskPolicy = policy

    assertEquals(
      RiskEngine.decisionForScore(policy.reviewThreshold - 1),
      RiskDecision.Approve
    )
    assertEquals(
      RiskEngine.decisionForScore(policy.reviewThreshold),
      RiskDecision.Review
    )
    assertEquals(
      RiskEngine.decisionForScore(policy.blockThreshold - 1),
      RiskDecision.Review
    )
    assertEquals(
      RiskEngine.decisionForScore(policy.blockThreshold),
      RiskDecision.Block
    )
  }

  test("evaluate ignores eligibility-only conditions") {
    val normalizedEvent = event.copy(
      amount = BigDecimal("6000.00"),
      paymentMethod = PaymentMethod.Card
    )
    val profile = customer.copy(isActive = false)

    assertEquals(assess(enrichedWith(normalizedEvent, profile)).alerts, Nil)
  }

  test("evaluate flags customer with previous fraud history") {
    val assessment = assess(enrichedWith(profile = customer.copy(fraudBefore = true)))

    assertSingleAlert(
      assessment,
      alertType = AlertType.PreviouslyFlaggedCustomer,
      riskScore = 20,
      decision = RiskDecision.Review
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

  test("evaluate normalizes customer countries before country mismatch check") {
    val profile = customer.copy(country = " pl ", lastLoginCountry = " de ")
    val normalizedEvent = event.copy(transactionCountry = "PL")

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
      decision = RiskDecision.Review
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

  test("evaluate flags velocity spike") {
    val riskContext =
      context.copy(transactionCountLastHour = RiskPolicy.default.velocityTransactionThreshold - 1)

    assertSingleAlert(
      assess(enriched, riskContext),
      alertType = AlertType.VelocitySpike,
      riskScore = 30,
      decision = RiskDecision.Review
    )
  }

  test("evaluate flags failed attempt burst") {
    val riskContext =
      context.copy(failedAttemptCountLastHour = RiskPolicy.default.failedAttemptThreshold - 1)
    val normalizedEvent = event.copy(status = EventStatus.Failed)

    assertSingleAlert(
      assess(enrichedWith(normalizedEvent = normalizedEvent), riskContext),
      alertType = AlertType.FailedAttemptBurst,
      riskScore = 30,
      decision = RiskDecision.Review
    )
  }

  test("evaluate flags repeated late-night activity") {
    val riskContext =
      context.copy(lateNightTransactionCountLast7d = RiskPolicy.default.lateNightThreshold - 1)
    val normalizedEvent =
      event.copy(timestamp = Instant.parse("2026-04-24T02:30:00Z"))

    assertAlerts(
      assess(enrichedWith(normalizedEvent = normalizedEvent), riskContext),
      alertTypes = List(
        AlertType.LateNightTransaction,
        AlertType.RepeatedLateNightActivity
      ),
      riskScore = 30,
      decision = RiskDecision.Review
    )
  }

  test("evaluate flags high-risk new device") {
    val riskContext = context.copy(knownDevice = false)
    val normalizedEvent = event.copy(amount = BigDecimal("4500.00"))

    assertSingleAlert(
      assess(enrichedWith(normalizedEvent = normalizedEvent), riskContext),
      alertType = AlertType.NewDeviceHighRisk,
      riskScore = 20,
      decision = RiskDecision.Review
    )
  }

  test("evaluate flags amount outlier") {
    val riskContext = context.copy(
      averageAmount30d = Some(BigDecimal("100.00")),
      amountStddev30d = Some(BigDecimal("10.00")),
      historySize30d = RiskPolicy.default.amountOutlierMinHistory
    )

    assertSingleAlert(
      assess(enriched, riskContext),
      alertType = AlertType.AmountOutlier,
      riskScore = 25,
      decision = RiskDecision.Review
    )
  }

  test("evaluate does not flag senior method shift when customer is not senior") {
    val riskContext = context.copy(
      blikTransferCountLast24h = 6,
      blikTransferCountLast30d = 10,
      totalTransactionCountLast30d = 40
    )

    assertEquals(
      assess(enrichedWith(profile = customer.copy(age = 65)), riskContext).alerts.exists(
        _.alertType == AlertType.SeniorMethodShiftAnomaly
      ),
      false
    )
  }

  test("evaluate does not flag senior method shift for stable baseline usage") {
    val profile = customer.copy(age = 75)
    val riskContext = context.copy(
      blikTransferCountLast24h = 3,
      blikTransferCountLast30d = 90,
      totalTransactionCountLast30d = 120
    )

    assertEquals(
      assess(enrichedWith(profile = profile), riskContext).alerts.exists(
        _.alertType == AlertType.SeniorMethodShiftAnomaly
      ),
      false
    )
  }

  test("evaluate flags senior method shift anomaly on abrupt Blik spike") {
    val profile = customer.copy(age = 75)
    val riskContext = context.copy(
      blikTransferCountLast24h = 4,
      blikTransferCountLast30d = 30,
      totalTransactionCountLast30d = 120
    )

    assertSingleAlert(
      assess(enrichedWith(profile = profile), riskContext),
      alertType = AlertType.SeniorMethodShiftAnomaly,
      riskScore = RiskPolicy.default.seniorMethodShiftScore,
      decision = RiskDecision.Approve
    )
  }

  test("evaluate does not flag senior method shift when method is Card") {
    val profile = customer.copy(age = 78)
    val normalizedEvent = event.copy(paymentMethod = PaymentMethod.Card)
    val riskContext = context.copy(
      blikTransferCountLast24h = 6,
      blikTransferCountLast30d = 20,
      totalTransactionCountLast30d = 100
    )

    assertEquals(
      assess(enrichedWith(normalizedEvent, profile), riskContext).alerts.exists(
        _.alertType == AlertType.SeniorMethodShiftAnomaly
      ),
      false
    )
  }

  test("evaluate returns deterministic multi-alert score and block decision") {
    val normalizedEvent = event.copy(
      timestamp = Instant.parse("2026-04-24T02:30:00Z"),
      amount = BigDecimal("4500.00"),
      status = EventStatus.Failed,
      transactionCountry = "US"
    )
    val profile = customer.copy(
      fraudBefore = true,
      createdAt = normalizedEvent.timestamp.minusSeconds(24L * 60L * 60L)
    )
    val riskContext = context.copy(
      transactionCountLastHour = RiskPolicy.default.velocityTransactionThreshold - 1,
      failedAttemptCountLastHour = RiskPolicy.default.failedAttemptThreshold - 1,
      lateNightTransactionCountLast7d = RiskPolicy.default.lateNightThreshold - 1,
      knownDevice = false,
      averageAmount30d = Some(BigDecimal("100.00")),
      amountStddev30d = Some(BigDecimal("10.00")),
      historySize30d = RiskPolicy.default.amountOutlierMinHistory
    )

    assertAlerts(
      assess(enrichedWith(normalizedEvent, profile), riskContext),
      alertTypes = List(
        AlertType.PreviouslyFlaggedCustomer,
        AlertType.CountryMismatch,
        AlertType.NewAccountHighAmount,
        AlertType.LateNightTransaction,
        AlertType.VelocitySpike,
        AlertType.FailedAttemptBurst,
        AlertType.RepeatedLateNightActivity,
        AlertType.NewDeviceHighRisk,
        AlertType.AmountOutlier
      ),
      riskScore = 190,
      decision = RiskDecision.Block
    )
  }

  private def assertSingleAlert(
      assessment: RiskAssessment,
      alertType: AlertType,
      riskScore: Int,
      decision: RiskDecision
  ): Unit =
    assertAlerts(
      assessment,
      alertTypes = List(alertType),
      riskScore = riskScore,
      decision = decision
    )

  private def assertAlerts(
      assessment: RiskAssessment,
      alertTypes: List[AlertType],
      riskScore: Int,
      decision: RiskDecision
  ): Unit =
    val actualAlertTypes = assessment.alerts.map(_.alertType).sortBy(_.toString)
    val expectedAlertTypes = alertTypes.sortBy(_.toString)

    assertEquals(assessment.riskScore, riskScore)
    assertEquals(assessment.decision, decision)
    assertEquals(actualAlertTypes, expectedAlertTypes)
    assertEquals(assessment.alerts.map(_.riskScore).sum, riskScore)
end RiskEngineTest
