package com.team.pipeline.application.risk

import cats.effect.IO
import com.team.pipeline.domain.EnrichedPaymentEvent

trait RiskContextProvider:
  def contextFor(event: EnrichedPaymentEvent): IO[CustomerRiskContext]
