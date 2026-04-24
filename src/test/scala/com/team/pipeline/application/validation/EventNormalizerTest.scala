package com.team.pipeline.application.validation

import com.team.pipeline.domain.{
  EventStatus,
  InvalidPaymentMethodFlags,
  InvalidStatus,
  InvalidTimestamp,
  NoPaymentMethodSelected,
  PaymentMethod,
  RawPaymentEvent
}

import munit.FunSuite

class EventNormalizerTest extends FunSuite:

  private val validRaw = RawPaymentEvent(
    eventId = 1,
    timestamp = "2024-01-01T10:15:30Z",
    customerId = 10,
    amount = BigDecimal(12.34),
    status = 1,
    hasBlik = 1,
    hasCard = 0,
    hasTransfer = 1
  )

  test("normalize: happy path") {
    val out = EventNormalizer.default.normalize(validRaw)
    assert(out.isRight)

    val normalized = out.toOption.get
    assertEquals(normalized.eventId, 1)
    assertEquals(normalized.customerId, 10)
    assertEquals(normalized.amount, BigDecimal(12.34))
    assertEquals(normalized.status, EventStatus.Success)
    assertEquals(
      normalized.paymentMethods,
      Set(PaymentMethod.Blik, PaymentMethod.Transfer)
    )
  }

  test("normalizeEnumKey: trims and lowercases") {
    assertEquals(EventNormalizer.normalizeEnumKey("  SUCCESS  "), "success")
  }

  test("normalizeCurrencyCode: trims and uppercases") {
    assertEquals(EventNormalizer.normalizeCurrencyCode("  pln  "), "PLN")
  }

  test("normalizeTimestamp: invalid instant yields InvalidTimestamp") {
    val out = EventNormalizer.normalizeTimestamp("not-a-timestamp")
    assertEquals(out, Left(InvalidTimestamp("not-a-timestamp")))
  }

  test("normalizeStatus: unknown status yields InvalidStatus") {
    val out = EventNormalizer.normalizeStatus(2)
    assertEquals(out, Left(InvalidStatus(2)))
  }

  test(
    "normalizePaymentMethodFlags: non-boolean flags yield InvalidPaymentMethodFlags"
  ) {
    val out = EventNormalizer.normalizePaymentMethodFlags(2, 0, 1)
    assertEquals(out, Left(InvalidPaymentMethodFlags(2, 0, 1)))
  }

  test(
    "normalizePaymentMethodFlags: no selected method yields NoPaymentMethodSelected"
  ) {
    val out = EventNormalizer.normalizePaymentMethodFlags(0, 0, 0)
    assertEquals(out, Left(NoPaymentMethodSelected))
  }
