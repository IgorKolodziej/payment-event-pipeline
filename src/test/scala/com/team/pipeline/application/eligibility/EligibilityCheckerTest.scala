package com.team.pipeline.application.eligibility

import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.EventId
import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.EligibilityAssessment
import com.team.pipeline.domain.EligibilityDecision
import com.team.pipeline.domain.EligibilityViolationType
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import munit.FunSuite

import java.time.Instant

class EligibilityCheckerTest extends FunSuite:
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

  private def assess(
      enrichedEvent: EnrichedPaymentEvent,
      riskContext: CustomerRiskContext = context
  ) =
    EligibilityChecker.evaluate(enrichedEvent, riskContext)

  test("evaluate returns eligible when no business rules fail") {
    val assessment = assess(enrichedWith())

    assertEquals(assessment.decision, EligibilityDecision.Eligible)
    assertEquals(assessment.violations, Nil)
  }

  test("evaluate accepts same-currency successful payments") {
    val assessment = assess(enrichedWith(event.copy(currency = Currency.PLN)))

    assertEquals(assessment.decision, EligibilityDecision.Eligible)
    assertEquals(assessment.violations, Nil)
  }

  test("evaluate declines account currency mismatch") {
    assertSingleViolation(
      assess(enrichedWith(event.copy(currency = Currency.EUR))),
      EligibilityViolationType.CurrencyMismatch
    )
  }

  test("evaluate does not run money comparisons when currencies differ") {
    val normalizedEvent = event.copy(
      amount = BigDecimal("10000.00"),
      currency = Currency.EUR
    )
    val profile = customer.copy(
      balance = BigDecimal("10.00"),
      dailyLimit = BigDecimal("20.00")
    )
    val riskContext = context.copy(approvedAmountLast24h = BigDecimal("10000.00"))

    assertSingleViolation(
      assess(enrichedWith(normalizedEvent, profile), riskContext),
      EligibilityViolationType.CurrencyMismatch
    )
  }

  test("evaluate declines inactive customer") {
    assertSingleViolation(
      assess(enrichedWith(profile = customer.copy(isActive = false))),
      EligibilityViolationType.InactiveCustomer
    )
  }

  test("evaluate declines successful event with insufficient balance") {
    val normalizedEvent = event.copy(amount = BigDecimal("5600.00"))
    val profile = customer.copy(balance = BigDecimal("5500.00"), dailyLimit = BigDecimal("7000.00"))

    assertSingleViolation(
      assess(enrichedWith(normalizedEvent, profile)),
      EligibilityViolationType.InsufficientBalance
    )
  }

  test("evaluate declines successful event above single transaction limit") {
    val normalizedEvent = event.copy(amount = BigDecimal("5000.01"))
    val profile = customer.copy(balance = BigDecimal("10000.00"))

    assertSingleViolation(
      assess(enrichedWith(normalizedEvent, profile)),
      EligibilityViolationType.SingleTransactionLimitExceeded
    )
  }

  test("evaluate declines successful event that would exceed daily limit") {
    val riskContext = context.copy(approvedAmountLast24h = BigDecimal("4900.00"))

    assertSingleViolation(
      assess(enrichedWith(), riskContext),
      EligibilityViolationType.DailyLimitExceeded
    )
  }

  test("evaluate does not count failed current event toward daily limit") {
    val riskContext = context.copy(approvedAmountLast24h = BigDecimal("4900.00"))
    val normalizedEvent = event.copy(
      status = EventStatus.Failed,
      amount = BigDecimal("200.00")
    )

    assertEquals(assess(enrichedWith(normalizedEvent), riskContext).violations, Nil)
  }

  test("evaluate declines disabled payment method") {
    val normalizedEvent = event.copy(paymentMethod = PaymentMethod.Card)

    assertSingleViolation(
      assess(enrichedWith(normalizedEvent)),
      EligibilityViolationType.PaymentMethodNotAllowed
    )
  }

  test("evaluate returns deterministic multiple violations") {
    val normalizedEvent = event.copy(
      amount = BigDecimal("6000.00"),
      paymentMethod = PaymentMethod.Card
    )
    val profile = customer.copy(isActive = false)

    val assessment = assess(enrichedWith(normalizedEvent, profile))

    assertEquals(assessment.decision, EligibilityDecision.Declined)
    assertEquals(
      assessment.violations.map(_.violationType),
      List(
        EligibilityViolationType.InactiveCustomer,
        EligibilityViolationType.InsufficientBalance,
        EligibilityViolationType.SingleTransactionLimitExceeded,
        EligibilityViolationType.PaymentMethodNotAllowed
      )
    )
  }

  private def assertSingleViolation(
      assessment: EligibilityAssessment,
      violationType: EligibilityViolationType
  ): Unit =
    assertEquals(assessment.decision, EligibilityDecision.Declined)
    assertEquals(assessment.violations.map(_.violationType), List(violationType))
end EligibilityCheckerTest
