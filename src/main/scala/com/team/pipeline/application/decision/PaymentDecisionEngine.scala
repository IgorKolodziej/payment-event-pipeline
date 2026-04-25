package com.team.pipeline.application.decision

import com.team.pipeline.application.eligibility.EligibilityChecker
import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.application.risk.RiskEngine
import com.team.pipeline.application.risk.RiskPolicy
import com.team.pipeline.domain.EligibilityDecision
import com.team.pipeline.domain.EnrichedPaymentEvent
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
          finalDecision = finalDecisionFor(risk)
        )

  private def finalDecisionFor(risk: RiskAssessment): FinalDecision =
    risk.decision match
      case RiskDecision.Approve => FinalDecision.Accepted
      case RiskDecision.Review  => FinalDecision.Review
      case RiskDecision.Block   => FinalDecision.BlockedByRisk
