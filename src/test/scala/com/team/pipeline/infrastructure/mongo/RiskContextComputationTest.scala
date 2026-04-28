package com.team.pipeline.infrastructure.mongo

import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.infrastructure.mongo.RiskContextComputation.HistoryEvent
import munit.FunSuite

import java.time.Instant

class RiskContextComputationTest extends FunSuite:

  private val now = Instant.parse("2026-04-24T10:00:00Z")

  private val event = NormalizedPaymentEvent(
    eventId = 100,
    timestamp = now,
    customerId = 10,
    amount = BigDecimal("150.00"),
    currency = Currency.PLN,
    status = EventStatus.Success,
    paymentMethod = PaymentMethod.Blik,
    transactionCountry = "PL",
    merchantId = "M1",
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
    hashedCustomerEmail = "hashed"
  )

  private def historyEvent(
      id: Int,
      ts: Instant,
      amount: BigDecimal = BigDecimal("10.00"),
      status: EventStatus = EventStatus.Success,
      method: PaymentMethod = PaymentMethod.Card,
      deviceId: String = "device-old",
      finalDecision: FinalDecision = FinalDecision.Accepted
  ): HistoryEvent =
    HistoryEvent(
      eventId = id,
      timestamp = ts,
      amount = amount,
      status = status,
      paymentMethod = method,
      deviceId = deviceId,
      finalDecision = finalDecision
    )

  test("compute uses [from,to) windows and excludes current eventId") {
    val oneHourAgo = Instant.parse("2026-04-24T09:00:00Z")
    val justBeforeHour = Instant.parse("2026-04-24T08:59:59Z")

    val history = List(
      historyEvent(1, oneHourAgo),
      historyEvent(2, justBeforeHour),
      historyEvent(100, oneHourAgo) // same eventId as current; must be excluded
    )

    val ctx = RiskContextComputation.compute(enriched, history)

    assertEquals(ctx.transactionCountLastHour, 1)
    assertEquals(ctx.totalTransactionCountLast30d, 2)
  }

  test("compute counts failed attempts in last hour") {
    val history = List(
      historyEvent(1, Instant.parse("2026-04-24T09:30:00Z"), status = EventStatus.Failed),
      historyEvent(2, Instant.parse("2026-04-24T09:40:00Z"), status = EventStatus.Success)
    )

    val ctx = RiskContextComputation.compute(enriched, history)

    assertEquals(ctx.failedAttemptCountLastHour, 1)
    assertEquals(ctx.transactionCountLastHour, 2)
  }

  test("compute sums approvedAmountLast24h using accepted+successful only") {
    val history = List(
      historyEvent(
        1,
        Instant.parse("2026-04-24T01:00:00Z"),
        amount = BigDecimal("100.00"),
        status = EventStatus.Success,
        finalDecision = FinalDecision.Accepted
      ),
      historyEvent(
        2,
        Instant.parse("2026-04-24T02:00:00Z"),
        amount = BigDecimal("200.00"),
        status = EventStatus.Success,
        finalDecision = FinalDecision.BlockedByRisk
      ),
      historyEvent(
        3,
        Instant.parse("2026-04-24T03:00:00Z"),
        amount = BigDecimal("300.00"),
        status = EventStatus.Failed,
        finalDecision = FinalDecision.Accepted
      )
    )

    val ctx = RiskContextComputation.compute(enriched, history)

    assertEquals(ctx.approvedAmountLast24h, BigDecimal("100.00"))
  }

  test("compute detects knownDevice from any prior event") {
    val history = List(
      historyEvent(1, Instant.parse("2026-04-20T10:00:00Z"), deviceId = "device-001")
    )

    val ctx = RiskContextComputation.compute(enriched, history)

    assertEquals(ctx.knownDevice, true)
  }

  test("compute calculates average and stddev from accepted-success amounts in last 30d") {
    val history = List(
      historyEvent(
        1,
        Instant.parse("2026-04-10T10:00:00Z"),
        amount = BigDecimal("100"),
        status = EventStatus.Success,
        finalDecision = FinalDecision.Accepted
      ),
      historyEvent(
        2,
        Instant.parse("2026-04-11T10:00:00Z"),
        amount = BigDecimal("200"),
        status = EventStatus.Success,
        finalDecision = FinalDecision.Accepted
      ),
      historyEvent(
        3,
        Instant.parse("2026-04-12T10:00:00Z"),
        amount = BigDecimal("999"),
        status = EventStatus.Success,
        finalDecision = FinalDecision.Declined
      )
    )

    val ctx = RiskContextComputation.compute(enriched, history)

    assertEquals(ctx.historySize30d, 2)
    assertEquals(ctx.averageAmount30d, Some(BigDecimal("150")))
    assertEquals(ctx.amountStddev30d, Some(BigDecimal("50")))
  }

  test("compute counts blik/transfer and late-night activity") {
    val history = List(
      historyEvent(
        1,
        Instant.parse("2026-04-24T02:00:00Z"),
        method = PaymentMethod.Blik
      ),
      historyEvent(
        2,
        Instant.parse("2026-04-23T11:00:00Z"),
        method = PaymentMethod.Transfer
      ),
      historyEvent(
        3,
        Instant.parse("2026-04-23T11:00:00Z"),
        method = PaymentMethod.Card
      )
    )

    val ctx = RiskContextComputation.compute(enriched, history)

    assertEquals(ctx.blikTransferCountLast24h, 2)
    assertEquals(ctx.blikTransferCountLast30d, 2)
    assertEquals(ctx.lateNightTransactionCountLast7d, 1)
  }
