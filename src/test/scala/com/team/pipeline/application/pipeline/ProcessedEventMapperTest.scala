package com.team.pipeline.application.pipeline

import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.AlertType
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.EligibilityAssessment
import com.team.pipeline.domain.EligibilityDecision
import com.team.pipeline.domain.EligibilityViolation
import com.team.pipeline.domain.EligibilityViolationType
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.PaymentAssessment
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.RiskAssessment
import com.team.pipeline.domain.RiskDecision
import munit.FunSuite

import java.time.Instant

class ProcessedEventMapperTest extends FunSuite:
  private val normalized = NormalizedPaymentEvent(
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
    event = normalized,
    customer = customer,
    hashedCustomerEmail = "hashed-email"
  )

  private val eligible =
    EligibilityAssessment(
      decision = EligibilityDecision.Eligible,
      violations = Nil
    )

  test("from maps accepted assessment to processed event") {
    val assessment = PaymentAssessment(
      eligibility = eligible,
      risk = Some(RiskAssessment(riskScore = 0, decision = RiskDecision.Approve, alerts = Nil)),
      finalDecision = FinalDecision.Accepted
    )

    val processed = ProcessedEventMapper.from(enriched, assessment)

    assertEquals(processed.eventId, normalized.eventId)
    assertEquals(processed.customerId, normalized.customerId)
    assertEquals(processed.timestamp, normalized.timestamp)
    assertEquals(processed.amount, normalized.amount)
    assertEquals(processed.currency, normalized.currency)
    assertEquals(processed.status, normalized.status)
    assertEquals(processed.paymentMethod, normalized.paymentMethod)
    assertEquals(processed.transactionCountry, normalized.transactionCountry)
    assertEquals(processed.merchantId, normalized.merchantId)
    assertEquals(processed.merchantCategory, normalized.merchantCategory)
    assertEquals(processed.channel, normalized.channel)
    assertEquals(processed.deviceId, normalized.deviceId)
    assertEquals(processed.customerCountry, customer.country)
    assertEquals(processed.hashedCustomerEmail, enriched.hashedCustomerEmail)
    assertEquals(processed.riskScore, 0)
    assertEquals(processed.riskDecision, RiskDecision.Approve)
    assertEquals(processed.finalDecision, FinalDecision.Accepted)
  }

  test("from maps reviewed risk assessment") {
    val alert = Alert(
      alertType = AlertType.VelocitySpike,
      eventId = normalized.eventId,
      customerId = normalized.customerId,
      message = "velocity",
      riskScore = 30
    )
    val assessment = PaymentAssessment(
      eligibility = eligible,
      risk = Some(
        RiskAssessment(
          riskScore = 30,
          decision = RiskDecision.Review,
          alerts = List(alert)
        )
      ),
      finalDecision = FinalDecision.Review
    )

    val processed = ProcessedEventMapper.from(enriched, assessment)

    assertEquals(processed.riskScore, 30)
    assertEquals(processed.riskDecision, RiskDecision.Review)
    assertEquals(processed.finalDecision, FinalDecision.Review)
  }

  test("from uses NotEvaluated when eligibility declined and risk is absent") {
    val violation = EligibilityViolation(
      violationType = EligibilityViolationType.InsufficientBalance,
      eventId = normalized.eventId,
      customerId = normalized.customerId,
      message = "insufficient balance"
    )
    val assessment = PaymentAssessment(
      eligibility = EligibilityAssessment(
        decision = EligibilityDecision.Declined,
        violations = List(violation)
      ),
      risk = None,
      finalDecision = FinalDecision.Declined
    )

    val processed = ProcessedEventMapper.from(enriched, assessment)

    assertEquals(processed.riskScore, 0)
    assertEquals(processed.riskDecision, RiskDecision.NotEvaluated)
    assertEquals(processed.finalDecision, FinalDecision.Declined)
  }
end ProcessedEventMapperTest
