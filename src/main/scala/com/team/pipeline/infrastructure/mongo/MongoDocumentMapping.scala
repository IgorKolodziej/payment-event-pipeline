package com.team.pipeline.infrastructure.mongo

import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.CustomerId.*
import com.team.pipeline.domain.EligibilityViolation
import com.team.pipeline.domain.EventId.*
import com.team.pipeline.domain.ProcessedEvent
import org.bson.Document

import java.util.Date

object MongoDocumentMapping:

  object ProcessedEventDoc:
    def toDocument(event: ProcessedEvent): Document =
      Document()
        .append("eventId", event.eventId.value)
        .append("customerId", event.customerId.value)
        .append("timestamp", Date.from(event.timestamp))
        .append("amount", event.amount.bigDecimal.toPlainString)
        .append("currency", event.currency.code)
        .append("status", event.status.code)
        .append("paymentMethod", event.paymentMethod.code)
        .append("transactionCountry", event.transactionCountry)
        .append("merchantId", event.merchantId)
        .append("merchantCategory", event.merchantCategory.code)
        .append("channel", event.channel.code)
        .append("deviceId", event.deviceId)
        .append("customerCountry", event.customerCountry)
        .append("customerAccountCurrency", event.customerAccountCurrency.code)
        .append("hashedCustomerEmail", event.hashedCustomerEmail)
        .append("riskScore", event.riskScore)
        .append("riskDecision", event.riskDecision.code)
        .append("finalDecision", event.finalDecision.code)

  object EligibilityViolationDoc:
    def toDocument(violation: EligibilityViolation): Document =
      Document()
        .append("eventId", violation.eventId.value)
        .append("customerId", violation.customerId.value)
        .append("violationType", violation.violationType.code)
        .append("message", violation.message)

  object AlertDoc:
    def toDocument(alert: Alert): Document =
      Document()
        .append("eventId", alert.eventId.value)
        .append("customerId", alert.customerId.value)
        .append("alertType", alert.alertType.code)
        .append("message", alert.message)
        .append("riskScore", alert.riskScore)
