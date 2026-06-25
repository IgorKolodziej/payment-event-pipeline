package com.team.pipeline.ports

import cats.effect.IO
import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.EventId

import java.time.Instant

trait RiskHistoryProvider:
  def historyFor(
      customerId: CustomerId,
      fromInclusive: Instant,
      toExclusive: Instant,
      excludeEventId: EventId
  ): IO[List[RiskHistoryEvent]]
