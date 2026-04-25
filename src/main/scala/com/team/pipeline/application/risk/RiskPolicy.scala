package com.team.pipeline.application.risk

final case class RiskPolicy(
    reviewThreshold: Int,
    blockThreshold: Int,
    newAccountHours: Long,
    highAmountDailyLimitRatio: BigDecimal,
    velocityTransactionThreshold: Int,
    failedAttemptThreshold: Int,
    lateNightStartHour: Int,
    lateNightEndHour: Int,
    lateNightThreshold: Int,
    amountOutlierMinHistory: Int,
    amountOutlierStddevMultiplier: BigDecimal,
    seniorAgeThreshold: Int,
    seniorMethodShiftMinHistory: Int,
    seniorMethodShiftMinRecentCount: Int,
    seniorMethodShiftMultiplier: BigDecimal,
    seniorMethodShiftScore: Int
)

object RiskPolicy:
  val default: RiskPolicy = RiskPolicy(
    reviewThreshold = 20,
    blockThreshold = 80,
    newAccountHours = 48,
    highAmountDailyLimitRatio = BigDecimal("0.80"),
    velocityTransactionThreshold = 5,
    failedAttemptThreshold = 3,
    lateNightStartHour = 0,
    lateNightEndHour = 5,
    lateNightThreshold = 3,
    amountOutlierMinHistory = 5,
    amountOutlierStddevMultiplier = BigDecimal("3.0"),
    seniorAgeThreshold = 70,
    seniorMethodShiftMinHistory = 20,
    seniorMethodShiftMinRecentCount = 3,
    seniorMethodShiftMultiplier = BigDecimal("3.0"),
    seniorMethodShiftScore = 15
  )
