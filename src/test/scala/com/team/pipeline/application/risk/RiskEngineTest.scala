package com.team.pipeline.application.risk

import com.team.pipeline.domain.*
import munit.FunSuite

import java.time.{Duration, Instant}

class RiskEngineTest extends FunSuite:

  private val baseCustomer: CustomerProfile = CustomerProfile(
    customerId = 1,
    firstName = "A",
    lastName = "B",
    email = "a@b.com",
    country = "PL",
    balance = BigDecimal(0),
    dailyLimit = BigDecimal(1000),
    allowedPaymentMethods = Set(PaymentMethod.Blik, PaymentMethod.Card, PaymentMethod.Transfer),
    isActive = true,
    age = 30,
    gender = "X",
    lastLoginCountry = "PL",
    fraudBefore = false
  )

  private def enriched(
      eventId: Int,
      ts: Instant,
      amount: BigDecimal,
      methods: Set[PaymentMethod],
      customer: CustomerProfile = baseCustomer
  ): EnrichedPaymentEvent =
    EnrichedPaymentEvent(
      event = NormalizedPaymentEvent(
        eventId = eventId,
        timestamp = ts,
        customerId = customer.customerId,
        amount = amount,
        status = EventStatus.Success,
        paymentMethods = methods
      ),
      customer = customer,
      hashedCustomerEmail = "hash"
    )

  test("inactive customer triggers InactiveCustomer"):
    val ev = enriched(1, Instant.parse("2026-04-24T10:00:00Z"), 100, Set(PaymentMethod.Card),
      baseCustomer.copy(isActive = false)
    )

    val assessment = RiskEngine.evaluate(ev)
    assert(assessment.alerts.exists(_.alertType == AlertType.InactiveCustomer))
    assertEquals(assessment.riskScore, RiskEngine.weights(AlertType.InactiveCustomer))

  test("amount above dailyLimit triggers LimitExceeded"):
    val ev = enriched(1, Instant.parse("2026-04-24T10:00:00Z"), 1500, Set(PaymentMethod.Card))
    val assessment = RiskEngine.evaluate(ev)

    assert(assessment.alerts.exists(_.alertType == AlertType.LimitExceeded))
    assertEquals(assessment.riskScore, RiskEngine.weights(AlertType.LimitExceeded))

  test("method not enabled triggers InvalidPaymentMethod"):
    val customer = baseCustomer.copy(allowedPaymentMethods = Set(PaymentMethod.Card))
    val ev = enriched(1, Instant.parse("2026-04-24T10:00:00Z"), 100, Set(PaymentMethod.Transfer), customer)
    val assessment = RiskEngine.evaluate(ev)

    assert(assessment.alerts.exists(_.alertType == AlertType.InvalidPaymentMethod))
    assertEquals(assessment.riskScore, RiskEngine.weights(AlertType.InvalidPaymentMethod))

  test("fraudBefore triggers PreviouslyFlaggedCustomer"):
    val ev = enriched(1, Instant.parse("2026-04-24T10:00:00Z"), 100, Set(PaymentMethod.Card),
      baseCustomer.copy(fraudBefore = true)
    )
    val assessment = RiskEngine.evaluate(ev)

    assert(assessment.alerts.exists(_.alertType == AlertType.PreviouslyFlaggedCustomer))
    assertEquals(assessment.riskScore, RiskEngine.weights(AlertType.PreviouslyFlaggedCustomer))

  test("country != lastLoginCountry triggers CountryLoginMismatch"):
    val ev = enriched(1, Instant.parse("2026-04-24T10:00:00Z"), 100, Set(PaymentMethod.Card),
      baseCustomer.copy(lastLoginCountry = "DE")
    )
    val assessment = RiskEngine.evaluate(ev)

    assert(assessment.alerts.exists(_.alertType == AlertType.CountryLoginMismatch))
    assertEquals(assessment.riskScore, RiskEngine.weights(AlertType.CountryLoginMismatch))

  test("new account near limit triggers NewAccountNearLimit"):
    val now = Instant.parse("2026-04-24T10:00:00Z")
    val createdAt = now.minus(Duration.ofHours(12))
    val customer = baseCustomer.copy(createdAt = Some(createdAt))

    val ev = enriched(1, now, 900, Set(PaymentMethod.Card), customer)
    val assessment = RiskEngine.evaluate(ev)

    assert(assessment.alerts.exists(_.alertType == AlertType.NewAccountNearLimit))
    assertEquals(assessment.riskScore, RiskEngine.weights(AlertType.NewAccountNearLimit))

  test("senior near limit using BLIK triggers SeniorNearLimitHighRiskMethod"):
    val customer = baseCustomer.copy(age = 80)
    val ev = enriched(1, Instant.parse("2026-04-24T10:00:00Z"), 900, Set(PaymentMethod.Blik), customer)
    val assessment = RiskEngine.evaluate(ev)

    assert(assessment.alerts.exists(_.alertType == AlertType.SeniorNearLimitHighRiskMethod))
    assertEquals(assessment.riskScore, RiskEngine.weights(AlertType.SeniorNearLimitHighRiskMethod))
