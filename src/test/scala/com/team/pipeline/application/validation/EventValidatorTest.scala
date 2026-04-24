package com.team.pipeline.application.validation

import cats.data.NonEmptyChain
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.InvalidAmount
import com.team.pipeline.domain.InvalidStatus
import com.team.pipeline.domain.InvalidTimestamp
import com.team.pipeline.domain.NoPaymentMethodSelected
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.RawPaymentEvent
import munit.FunSuite

import java.time.Instant

class EventValidatorTest extends FunSuite:
  private val validRaw = RawPaymentEvent(
    eventId = 1,
    timestamp = "2026-04-24T10:00:00Z",
    customerId = 10,
    amount = BigDecimal("150.00"),
    status = 0,
    hasBlik = 1,
    hasCard = 0,
    hasTransfer = 1
  )

  test("valid raw event produces normalized payment event") {
    val result = EventValidator.validateAndNormalize(validRaw)

    result match
      case Valid(event) =>
        assertEquals(event.eventId, 1)
        assertEquals(event.timestamp, Instant.parse("2026-04-24T10:00:00Z"))
        assertEquals(event.customerId, 10)
        assertEquals(event.amount, BigDecimal("150.00"))
        assertEquals(event.status, EventStatus.Success)
        assertEquals(event.paymentMethods, Set(PaymentMethod.Blik, PaymentMethod.Transfer))
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
    val raw = validRaw.copy(status = 2)

    assertEquals(
      EventValidator.validateAndNormalize(raw),
      Invalid(NonEmptyChain.one(InvalidStatus(2)))
    )
  }

  test("no selected payment method is reported") {
    val raw = validRaw.copy(hasBlik = 0, hasCard = 0, hasTransfer = 0)

    assertEquals(
      EventValidator.validateAndNormalize(raw),
      Invalid(NonEmptyChain.one(NoPaymentMethodSelected))
    )
  }

  test("multiple validation errors are accumulated") {
    val raw = validRaw.copy(
      timestamp = "bad-date-format",
      amount = BigDecimal("-5.00"),
      status = 3,
      hasBlik = 0,
      hasCard = 0,
      hasTransfer = 0
    )

    EventValidator.validateAndNormalize(raw) match
      case Invalid(errors) =>
        assertEquals(
          errors.toNonEmptyList.toList,
          List(
            InvalidTimestamp("bad-date-format"),
            InvalidAmount(BigDecimal("-5.00")),
            InvalidStatus(3),
            NoPaymentMethodSelected
          )
        )
      case Valid(event) =>
        fail(s"Expected validation to fail, but got: $event")
  }

  test("maps validation failure to rejected event") {
    val rejected = EventValidator.toRejected(
      lineNumber = 42,
      raw = validRaw,
      reason = InvalidAmount(BigDecimal("-100.00"))
    )

    assertEquals(rejected.lineNumber, 42L)
    assertEquals(rejected.eventId, Some(1))
    assertEquals(rejected.customerId, Some(10))
    assertEquals(rejected.reason, InvalidAmount(BigDecimal("-100.00")))
  }
end EventValidatorTest
