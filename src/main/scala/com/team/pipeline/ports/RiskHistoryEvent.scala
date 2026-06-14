package com.team.pipeline.ports

import com.team.pipeline.domain.{EventId, EventStatus, FinalDecision, PaymentMethod}

import java.time.Instant

final case class RiskHistoryEvent(
    eventId: EventId,
    timestamp: Instant,
    amount: BigDecimal,
    status: EventStatus,
    paymentMethod: PaymentMethod,
    deviceId: String,
    finalDecision: FinalDecision
)
