package com.team.pipeline.application.risk

import com.team.pipeline.domain.{AlertType, RiskAssessment}

object RiskDecision:

  enum Disposition:
    case Allow
    case ManualReview
    case Block

  final case class Config(
      manualReviewMinScore: Int = 1,
      blockMinScore: Int = 50,
      hardBlockAlertTypes: Set[AlertType] = Set(AlertType.InactiveCustomer)
  )

  val defaultConfig: Config = Config()

  def decide(assessment: RiskAssessment, config: Config = defaultConfig): Disposition =
    val hasHardBlock = assessment.alerts.exists(a => config.hardBlockAlertTypes.contains(a.alertType))

    if hasHardBlock || assessment.riskScore >= config.blockMinScore then Disposition.Block
    else if assessment.riskScore >= config.manualReviewMinScore then Disposition.ManualReview
    else Disposition.Allow
