package com.team.pipeline.application.decision

import com.team.pipeline.application.eligibility.EligibilityChecker
import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.application.risk.RiskEngine
import com.team.pipeline.application.risk.RiskPolicy
import com.team.pipeline.domain.EligibilityDecision
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.PaymentAssessment
import com.team.pipeline.domain.RiskAssessment
import com.team.pipeline.domain.RiskDecision

object PaymentDecisionEngine:
  def evaluate(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext,
      policy: RiskPolicy
  ): PaymentAssessment =
    val eligibility = EligibilityChecker.evaluate(event, context)

    eligibility.decision match
      case EligibilityDecision.Declined =>
        PaymentAssessment(
          eligibility = eligibility,
          risk = None,
          finalDecision = FinalDecision.Declined
        )
      case EligibilityDecision.Eligible =>
        val risk = RiskEngine.evaluate(event, context, policy)
        PaymentAssessment(
          eligibility = eligibility,
          risk = Some(risk),
          finalDecision = finalDecisionFor(event, risk)
        )

  private def finalDecisionFor(event: EnrichedPaymentEvent, risk: RiskAssessment): FinalDecision =
    risk.decision match
      case RiskDecision.Approve =>
        if event.event.status == EventStatus.Failed then FinalDecision.Declined
        else FinalDecision.Accepted
      case RiskDecision.Review       => FinalDecision.Review
      case RiskDecision.Block        => FinalDecision.BlockedByRisk
      case RiskDecision.NotEvaluated =>
        throw new IllegalStateException(
          "RiskDecision.NotEvaluated cannot be mapped from an evaluated risk assessment"
        )
