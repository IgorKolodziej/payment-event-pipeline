package com.team.pipeline.ports

import cats.effect.IO
import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.domain.EnrichedPaymentEvent

trait RiskFeatureProvider:
  def contextFor(event: EnrichedPaymentEvent): IO[CustomerRiskContext]
