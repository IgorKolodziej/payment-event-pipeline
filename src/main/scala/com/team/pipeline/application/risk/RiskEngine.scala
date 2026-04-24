package com.team.pipeline.application.risk

import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.RiskAssessment
import com.team.pipeline.domain.RiskDecision

object RiskEngine:
  def evaluate(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext,
      policy: RiskPolicy
  ): RiskAssessment =
    val alerts = List.empty[Alert]
    val riskScore = alerts.map(_.riskScore).sum

    RiskAssessment(
      riskScore = riskScore,
      decision = decisionForScore(riskScore, policy),
      alerts = alerts
    )

  private[risk] def decisionForScore(
      riskScore: Int,
      policy: RiskPolicy
  ): RiskDecision =
    if riskScore >= policy.blockThreshold then RiskDecision.Block
    else if riskScore >= policy.reviewThreshold then RiskDecision.Review
    else RiskDecision.Approve
