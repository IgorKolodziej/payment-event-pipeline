package com.team.pipeline.application.enrichment

import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.EventId
import com.team.pipeline.application.validation.EmailHasher
import com.team.pipeline.domain.CustomerNotFound
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import munit.FunSuite

import java.time.Instant

class EventEnricherTest extends FunSuite:
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
    email = "B.Krolak@Firma.PL",
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

  test("enrich creates enriched payment event with hashed customer email") {
    val salt = "test-salt"
    val enriched = EventEnricher.enrich(event, customer, salt)
    val expectedHash = EmailHasher.sha256(salt).hash(customer.email)

    assertEquals(enriched.event, event)
    assertEquals(enriched.customer, customer)
    assertEquals(enriched.hashedCustomerEmail, expectedHash)
    assert(enriched.hashedCustomerEmail != customer.email)
  }

  test("enrichOption enriches existing customer") {
    val salt = "test-salt"
    val enriched = EventEnricher.enrichOption(event, Some(customer), salt)
    val expected = Right(EventEnricher.enrich(event, customer, salt))

    assertEquals(enriched, expected)
  }

  test("enrichOption returns CustomerNotFound for missing customer") {
    val enriched = EventEnricher.enrichOption(event, None, "test-salt")

    assertEquals(enriched, Left(CustomerNotFound(event.customerId)))
  }
end EventEnricherTest
