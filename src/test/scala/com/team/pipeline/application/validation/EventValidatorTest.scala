package com.team.pipeline.application.validation

import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.EventId
import cats.data.NonEmptyChain
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.InvalidAmount
import com.team.pipeline.domain.InvalidCurrency
import com.team.pipeline.domain.InvalidMerchantCategory
import com.team.pipeline.domain.InvalidPaymentChannel
import com.team.pipeline.domain.InvalidPaymentMethod
import com.team.pipeline.domain.InvalidStatus
import com.team.pipeline.domain.InvalidTimestamp
import com.team.pipeline.domain.InvalidTransactionCountry
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.RawPaymentEvent
import munit.FunSuite

import java.time.Instant

class EventValidatorTest extends FunSuite:
  private val validRaw = RawPaymentEvent(
    eventId = EventId(1),
    timestamp = "2026-04-24T10:00:00Z",
    customerId = CustomerId(10),
    amount = BigDecimal("150.00"),
    currency = "PLN",
    status = "SUCCESS",
    paymentMethod = "BLIK",
    transactionCountry = "PL",
    merchantId = "M001",
    merchantCategory = "GROCERY",
    channel = "MOBILE",
    deviceId = "device-001"
  )

  test("valid raw event produces normalized payment event") {
    val result = EventValidator.validateAndNormalize(validRaw)

    result match
      case Valid(event) =>
        assertEquals(event.eventId, EventId(1))
        assertEquals(event.timestamp, Instant.parse("2026-04-24T10:00:00Z"))
        assertEquals(event.customerId, CustomerId(10))
        assertEquals(event.amount, BigDecimal("150.00"))
        assertEquals(event.currency, Currency.PLN)
        assertEquals(event.status, EventStatus.Success)
        assertEquals(event.paymentMethod, PaymentMethod.Blik)
        assertEquals(event.transactionCountry, "PL")
        assertEquals(event.merchantId, "M001")
        assertEquals(event.merchantCategory, MerchantCategory.Grocery)
        assertEquals(event.channel, PaymentChannel.Mobile)
        assertEquals(event.deviceId, "device-001")
      case Invalid(errors) =>
        fail(s"Expected validation to succeed, but got: $errors")
  }

  test("invalid timestamp is reported") {
    val raw = validRaw.copy(timestamp = "bad-date-format")

    assertEquals(
      EventValidator.validateAndNormalize(raw),
      Invalid(NonEmptyChain.one(InvalidTimestamp("bad-date-format")))
    )
  }

  test("non-positive amount is reported") {
    val raw = validRaw.copy(amount = BigDecimal("-100.00"))

    assertEquals(
      EventValidator.validateAndNormalize(raw),
      Invalid(NonEmptyChain.one(InvalidAmount(BigDecimal("-100.00"))))
    )
  }

  test("invalid status is reported") {
    val raw = validRaw.copy(status = "unknown")

    assertEquals(
      EventValidator.validateAndNormalize(raw),
      Invalid(NonEmptyChain.one(InvalidStatus("UNKNOWN")))
    )
  }

  test("invalid payment method is reported") {
    val raw = validRaw.copy(paymentMethod = "cash")

    assertEquals(
      EventValidator.validateAndNormalize(raw),
      Invalid(NonEmptyChain.one(InvalidPaymentMethod("CASH")))
    )
  }

  test("multiple validation errors are accumulated") {
    val raw = validRaw.copy(
      timestamp = "bad-date-format",
      amount = BigDecimal("-5.00"),
      currency = "xyz",
      status = "unknown",
      paymentMethod = "cash",
      transactionCountry = "POL",
      merchantCategory = "crypto",
      channel = "atm"
    )

    EventValidator.validateAndNormalize(raw) match
      case Invalid(errors) =>
        assertEquals(
          errors.toNonEmptyList.toList,
          List(
            InvalidTimestamp("bad-date-format"),
            InvalidAmount(BigDecimal("-5.00")),
            InvalidCurrency("XYZ"),
            InvalidStatus("UNKNOWN"),
            InvalidPaymentMethod("CASH"),
            InvalidTransactionCountry("POL"),
            InvalidMerchantCategory("CRYPTO"),
            InvalidPaymentChannel("ATM")
          )
        )
      case Valid(event) =>
        fail(s"Expected validation to fail, but got: $event")
  }

  test("maps validation failure to rejected event") {
    val rejected = EventValidator.toRejected(
      sourcePosition = 42,
      raw = validRaw,
      reasons = NonEmptyChain.one(InvalidAmount(BigDecimal("-100.00")))
    )

    assertEquals(rejected.sourcePosition, 42L)
    assertEquals(rejected.eventId, Some(EventId(1)))
    assertEquals(rejected.customerId, Some(CustomerId(10)))
    assertEquals(
      rejected.reasons,
      NonEmptyChain.one(InvalidAmount(BigDecimal("-100.00")))
    )
  }
end EventValidatorTest
