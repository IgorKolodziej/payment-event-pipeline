package com.team.pipeline.application.risk

import cats.effect.IO
import cats.effect.Ref
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventId
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.ports.{RiskHistoryEvent, RiskHistoryProvider}
import munit.CatsEffectSuite

import java.time.Duration
import java.time.Instant

class HistoryBackedRiskContextProviderTest extends CatsEffectSuite:

  test("contextFor loads the 30-day event history and computes risk context") {
    val history = List(
      RiskHistoryEvent(
        eventId = EventId(99),
        timestamp = now.minus(Duration.ofMinutes(30)),
        amount = BigDecimal("25.00"),
        status = EventStatus.Success,
        paymentMethod = PaymentMethod.Card,
        deviceId = "device-old",
        finalDecision = FinalDecision.Accepted
      )
    )

    for
      calls <- Ref[IO].of(Vector.empty[HistoryQuery])
      provider = HistoryBackedRiskContextProvider(
        RecordingRiskHistoryProvider(history, calls),
        RiskPolicy.default
      )
      context <- provider.contextFor(enriched)
      recordedCalls <- calls.get
    yield
      assertEquals(
        recordedCalls,
        Vector(
          HistoryQuery(
            customerId = CustomerId(10),
            fromInclusive = now.minus(Duration.ofDays(30)),
            toExclusive = now,
            excludeEventId = EventId(100)
          )
        )
      )
      assertEquals(context.transactionCountLastHour, 1)
      assertEquals(context.approvedAmountLast24h, BigDecimal("25.00"))
  }

  private final case class HistoryQuery(
      customerId: CustomerId,
      fromInclusive: Instant,
      toExclusive: Instant,
      excludeEventId: EventId
  )

  private final class RecordingRiskHistoryProvider(
      history: List[RiskHistoryEvent],
      calls: Ref[IO, Vector[HistoryQuery]]
  ) extends RiskHistoryProvider:
    override def historyFor(
        customerId: CustomerId,
        fromInclusive: Instant,
        toExclusive: Instant,
        excludeEventId: EventId
    ): IO[List[RiskHistoryEvent]] =
      calls.update(
        _ :+ HistoryQuery(
          customerId = customerId,
          fromInclusive = fromInclusive,
          toExclusive = toExclusive,
          excludeEventId = excludeEventId
        )
      ).as(history)

  private val now = Instant.parse("2026-04-24T10:00:00Z")

  private val event = NormalizedPaymentEvent(
    eventId = EventId(100),
    timestamp = now,
    customerId = CustomerId(10),
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
    hashedCustomerEmail = "hashed"
  )
