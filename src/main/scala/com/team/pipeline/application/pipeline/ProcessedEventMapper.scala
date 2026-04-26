package com.team.pipeline.application.pipeline

import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.PaymentAssessment
import com.team.pipeline.domain.ProcessedEvent
import com.team.pipeline.domain.RiskDecision

object ProcessedEventMapper:
  def from(
      event: EnrichedPaymentEvent,
      assessment: PaymentAssessment
  ): ProcessedEvent =
    val normalized = event.event
    val riskScore = assessment.risk.fold(0)(_.riskScore)
    val riskDecision = assessment.risk.fold(RiskDecision.NotEvaluated)(_.decision)

    ProcessedEvent(
      eventId = normalized.eventId,
      customerId = normalized.customerId,
      timestamp = normalized.timestamp,
      amount = normalized.amount,
      currency = normalized.currency,
      status = normalized.status,
      paymentMethod = normalized.paymentMethod,
      transactionCountry = normalized.transactionCountry,
      merchantId = normalized.merchantId,
      merchantCategory = normalized.merchantCategory,
      channel = normalized.channel,
      deviceId = normalized.deviceId,
      customerCountry = event.customer.country,
      hashedCustomerEmail = event.hashedCustomerEmail,
      riskScore = riskScore,
      riskDecision = riskDecision,
      finalDecision = assessment.finalDecision
    )
