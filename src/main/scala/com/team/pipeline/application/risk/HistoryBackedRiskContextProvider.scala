package com.team.pipeline.application.risk

import cats.effect.IO
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.ports.RiskHistoryProvider

import java.time.Duration

final class HistoryBackedRiskContextProvider(
    historyProvider: RiskHistoryProvider,
    policy: RiskPolicy = RiskPolicy.default
) extends RiskContextProvider:

  override def contextFor(event: EnrichedPaymentEvent): IO[CustomerRiskContext] =
    val now = event.event.timestamp

    historyProvider
      .historyFor(
        customerId = event.event.customerId,
        fromInclusive = now.minus(Duration.ofDays(30)),
        toExclusive = now,
        excludeEventId = event.event.eventId
      )
      .map(history => RiskContextComputation.compute(event, history, policy))
