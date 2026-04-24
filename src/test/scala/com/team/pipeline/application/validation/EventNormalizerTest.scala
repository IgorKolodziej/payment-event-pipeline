package com.team.pipeline.application.validation

import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.EventStatus
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

class EventNormalizerTest extends FunSuite:

  private val validRaw = RawPaymentEvent(
    eventId = 1,
    timestamp = "2024-01-01T10:15:30Z",
    customerId = 10,
    amount = BigDecimal("12.34"),
    currency = " pln ",
    status = " success ",
    paymentMethod = " blik ",
    transactionCountry = " pl ",
    merchantId = " M001 ",
    merchantCategory = " grocery ",
    channel = " mobile ",
    deviceId = " device-001 "
  )

  test("normalize: happy path") {
    val out = EventNormalizer.default.normalize(validRaw)
    assert(out.isRight)

    val normalized = out.toOption.get
    assertEquals(normalized.eventId, 1)
    assertEquals(normalized.customerId, 10)
    assertEquals(normalized.amount, BigDecimal("12.34"))
    assertEquals(normalized.currency, Currency.PLN)
    assertEquals(normalized.status, EventStatus.Success)
    assertEquals(normalized.paymentMethod, PaymentMethod.Blik)
    assertEquals(normalized.transactionCountry, "PL")
    assertEquals(normalized.merchantId, "M001")
    assertEquals(normalized.merchantCategory, MerchantCategory.Grocery)
    assertEquals(normalized.channel, PaymentChannel.Mobile)
    assertEquals(normalized.deviceId, "device-001")
  }

  test("normalizeEnumKey: trims and uppercases") {
    assertEquals(EventNormalizer.normalizeEnumKey("  success  "), "SUCCESS")
  }

  test("normalizeTimestamp: invalid instant yields InvalidTimestamp") {
    val out = EventNormalizer.normalizeTimestamp("not-a-timestamp")
    assertEquals(out, Left(InvalidTimestamp("not-a-timestamp")))
  }

  test("normalizeCurrency: unknown currency yields InvalidCurrency") {
    val out = EventNormalizer.normalizeCurrency("xyz")
    assertEquals(out, Left(InvalidCurrency("XYZ")))
  }

  test("normalizeStatus: failed status is supported") {
    assertEquals(EventNormalizer.normalizeStatus("failed"), Right(EventStatus.Failed))
  }

  test("normalizeStatus: unknown status yields InvalidStatus") {
    val out = EventNormalizer.normalizeStatus("unknown")
    assertEquals(out, Left(InvalidStatus("UNKNOWN")))
  }

  test("normalizePaymentMethod: unknown method yields InvalidPaymentMethod") {
    val out = EventNormalizer.normalizePaymentMethod("cash")
    assertEquals(out, Left(InvalidPaymentMethod("CASH")))
  }

  test("normalizeCountry: invalid country yields InvalidTransactionCountry") {
    val out = EventNormalizer.normalizeCountry("POL")
    assertEquals(out, Left(InvalidTransactionCountry("POL")))
  }

  test("normalizeMerchantCategory: unknown category yields InvalidMerchantCategory") {
    val out = EventNormalizer.normalizeMerchantCategory("crypto")
    assertEquals(out, Left(InvalidMerchantCategory("CRYPTO")))
  }

  test("normalizeChannel: unknown channel yields InvalidPaymentChannel") {
    val out = EventNormalizer.normalizeChannel("atm")
    assertEquals(out, Left(InvalidPaymentChannel("ATM")))
  }
end EventNormalizerTest
