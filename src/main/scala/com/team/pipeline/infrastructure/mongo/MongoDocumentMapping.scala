package com.team.pipeline.infrastructure.mongo

import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.EligibilityViolation
import com.team.pipeline.domain.ProcessedEvent
import org.bson.Document

import java.util.Date

object MongoDocumentMapping:

  object ProcessedEventDoc:
    def toDocument(event: ProcessedEvent): Document =
      Document()
        .append("eventId", event.eventId)
        .append("customerId", event.customerId)
        .append("timestamp", Date.from(event.timestamp))
        .append("amount", event.amount.bigDecimal.toPlainString)
        .append("currency", event.currency.toString)
        .append("status", event.status.toString)
        .append("paymentMethod", event.paymentMethod.toString)
        .append("transactionCountry", event.transactionCountry)
        .append("merchantId", event.merchantId)
        .append("merchantCategory", event.merchantCategory.toString)
        .append("channel", event.channel.toString)
        .append("deviceId", event.deviceId)
        .append("customerCountry", event.customerCountry)
        .append("customerAccountCurrency", event.customerAccountCurrency.toString)
        .append("hashedCustomerEmail", event.hashedCustomerEmail)
        .append("riskScore", event.riskScore)
        .append("riskDecision", event.riskDecision.toString)
        .append("finalDecision", event.finalDecision.toString)

  object EligibilityViolationDoc:
    def toDocument(violation: EligibilityViolation): Document =
      Document()
        .append("eventId", violation.eventId)
        .append("customerId", violation.customerId)
        .append("violationType", violation.violationType.toString)
        .append("message", violation.message)

  object AlertDoc:
    def toDocument(alert: Alert): Document =
      Document()
        .append("eventId", alert.eventId)
        .append("customerId", alert.customerId)
        .append("alertType", alert.alertType.toString)
        .append("message", alert.message)
        .append("riskScore", alert.riskScore)
