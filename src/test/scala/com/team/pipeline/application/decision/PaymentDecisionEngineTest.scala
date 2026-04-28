package com.team.pipeline.application.decision

import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.EventId
import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.application.risk.RiskPolicy
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.EligibilityDecision
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.RiskDecision
import munit.FunSuite

import java.time.Instant

class PaymentDecisionEngineTest extends FunSuite:
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

  private def enrichedWith(
      normalizedEvent: NormalizedPaymentEvent = event,
      profile: CustomerProfile = customer
  ): EnrichedPaymentEvent =
    EnrichedPaymentEvent(
      event = normalizedEvent,
      customer = profile,
      hashedCustomerEmail = "hashed-email"
    )

  test("evaluate returns accepted when eligibility passes and risk approves") {
    val assessment =
      PaymentDecisionEngine.evaluate(enrichedWith(), context, RiskPolicy.default)

    assertEquals(assessment.eligibility.decision, EligibilityDecision.Eligible)
    assertEquals(assessment.risk.map(_.decision), Some(RiskDecision.Approve))
    assertEquals(assessment.finalDecision, FinalDecision.Accepted)
  }

  test("evaluate declines failed event when eligibility passes and risk approves") {
    val normalizedEvent = event.copy(status = EventStatus.Failed)

    val assessment =
      PaymentDecisionEngine.evaluate(
        enrichedWith(normalizedEvent),
        context,
        RiskPolicy.default
      )

    assertEquals(assessment.eligibility.decision, EligibilityDecision.Eligible)
    assertEquals(assessment.risk.map(_.decision), Some(RiskDecision.Approve))
    assertEquals(assessment.finalDecision, FinalDecision.Declined)
  }

  test("evaluate declines and skips risk when eligibility fails") {
    val normalizedEvent = event.copy(amount = BigDecimal("6000.00"))
    val profile = customer.copy(balance = BigDecimal("5500.00"), dailyLimit = BigDecimal("7000.00"))

    val assessment =
      PaymentDecisionEngine.evaluate(
        enrichedWith(normalizedEvent, profile),
        context,
        RiskPolicy.default
      )

    assertEquals(assessment.eligibility.decision, EligibilityDecision.Declined)
    assertEquals(assessment.risk, None)
    assertEquals(assessment.finalDecision, FinalDecision.Declined)
  }

  test("evaluate returns review when eligible event reaches review risk threshold") {
    val profile = customer.copy(fraudBefore = true)

    val assessment =
      PaymentDecisionEngine.evaluate(enrichedWith(profile = profile), context, RiskPolicy.default)

    assertEquals(assessment.eligibility.decision, EligibilityDecision.Eligible)
    assertEquals(assessment.risk.map(_.decision), Some(RiskDecision.Review))
    assertEquals(assessment.finalDecision, FinalDecision.Review)
  }

  test("evaluate returns blocked by risk when eligible event reaches block threshold") {
    val normalizedEvent = event.copy(
      status = EventStatus.Failed,
      amount = BigDecimal("4500.00"),
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
      knownDevice = false
    )

    val assessment =
      PaymentDecisionEngine.evaluate(
        enrichedWith(normalizedEvent, profile),
        riskContext,
        RiskPolicy.default
      )

    assertEquals(assessment.eligibility.decision, EligibilityDecision.Eligible)
    assertEquals(assessment.risk.map(_.decision), Some(RiskDecision.Block))
    assertEquals(assessment.finalDecision, FinalDecision.BlockedByRisk)
  }
end PaymentDecisionEngineTest
