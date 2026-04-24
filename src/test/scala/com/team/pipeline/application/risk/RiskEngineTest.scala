package com.team.pipeline.application.risk

import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.Currency
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
end RiskEngineTest
