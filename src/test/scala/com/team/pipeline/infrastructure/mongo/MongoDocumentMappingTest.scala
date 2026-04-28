package com.team.pipeline.infrastructure.mongo

import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.EventId
import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.AlertType
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.EligibilityViolation
import com.team.pipeline.domain.EligibilityViolationType
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.ProcessedEvent
import com.team.pipeline.domain.RiskDecision
import munit.FunSuite

import java.time.Instant
import java.util.Date

class MongoDocumentMappingTest extends FunSuite:

  test("ProcessedEventDoc.toDocument maps processed event fields") {
    val processed = ProcessedEvent(
      eventId = EventId(1),
      customerId = CustomerId(10),
      timestamp = Instant.parse("2026-04-24T10:00:00Z"),
      amount = BigDecimal("150.00"),
      currency = Currency.PLN,
      status = EventStatus.Success,
      paymentMethod = PaymentMethod.Blik,
      transactionCountry = "PL",
      merchantId = "M1",
      merchantCategory = MerchantCategory.Grocery,
      channel = PaymentChannel.Mobile,
      deviceId = "device-1-1",
      customerCountry = "PL",
      customerAccountCurrency = Currency.PLN,
      hashedCustomerEmail = "hashed",
      riskScore = 25,
      riskDecision = RiskDecision.Review,
      finalDecision = FinalDecision.Review
    )

    val doc = MongoDocumentMapping.ProcessedEventDoc.toDocument(processed)

    assertEquals(doc.getInteger("eventId").intValue(), 1)
    assertEquals(doc.getInteger("customerId").intValue(), 10)
    assertEquals(doc.getDate("timestamp"), Date.from(processed.timestamp))
    assertEquals(doc.getString("amount"), "150.00")
    assertEquals(doc.getString("currency"), "PLN")
    assertEquals(doc.getString("status"), "SUCCESS")
    assertEquals(doc.getString("paymentMethod"), "BLIK")
    assertEquals(doc.getString("merchantCategory"), "GROCERY")
    assertEquals(doc.getString("channel"), "MOBILE")
    assertEquals(doc.getString("deviceId"), "device-1-1")
    assertEquals(doc.getString("customerAccountCurrency"), "PLN")
    assertEquals(doc.getString("hashedCustomerEmail"), "hashed")
    assertEquals(doc.getInteger("riskScore").intValue(), 25)
    assertEquals(doc.getString("riskDecision"), "Review")
    assertEquals(doc.getString("finalDecision"), "Review")
  }

  test("ProcessedEventDoc.toDocument maps not-evaluated risk decision") {
    val processed = ProcessedEvent(
      eventId = EventId(2),
      customerId = CustomerId(10),
      timestamp = Instant.parse("2026-04-24T10:00:00Z"),
      amount = BigDecimal("150.00"),
      currency = Currency.PLN,
      status = EventStatus.Success,
      paymentMethod = PaymentMethod.Blik,
      transactionCountry = "PL",
      merchantId = "M1",
      merchantCategory = MerchantCategory.Grocery,
      channel = PaymentChannel.Mobile,
      deviceId = "device-1-1",
      customerCountry = "PL",
      customerAccountCurrency = Currency.PLN,
      hashedCustomerEmail = "hashed",
      riskScore = 0,
      riskDecision = RiskDecision.NotEvaluated,
      finalDecision = FinalDecision.Declined
    )

    val doc = MongoDocumentMapping.ProcessedEventDoc.toDocument(processed)

    assertEquals(doc.getString("riskDecision"), "NotEvaluated")
    assertEquals(doc.getString("finalDecision"), "Declined")
  }

  test("EligibilityViolationDoc.toDocument maps stable violation key fields") {
    val violation = EligibilityViolation(
      violationType = EligibilityViolationType.InactiveCustomer,
      eventId = EventId(99),
      customerId = CustomerId(10),
      message = "inactive"
    )

    val doc = MongoDocumentMapping.EligibilityViolationDoc.toDocument(violation)

    assertEquals(doc.getInteger("eventId").intValue(), 99)
    assertEquals(doc.getInteger("customerId").intValue(), 10)
    assertEquals(doc.getString("violationType"), "InactiveCustomer")
    assertEquals(doc.getString("message"), "inactive")
  }

  test("AlertDoc.toDocument maps stable alert key fields") {
    val alert = Alert(
      alertType = AlertType.VelocitySpike,
      eventId = EventId(42),
      customerId = CustomerId(10),
      message = "velocity",
      riskScore = 30
    )

    val doc = MongoDocumentMapping.AlertDoc.toDocument(alert)

    assertEquals(doc.getInteger("eventId").intValue(), 42)
    assertEquals(doc.getInteger("customerId").intValue(), 10)
    assertEquals(doc.getString("alertType"), "VelocitySpike")
    assertEquals(doc.getString("message"), "velocity")
    assertEquals(doc.getInteger("riskScore").intValue(), 30)
  }
