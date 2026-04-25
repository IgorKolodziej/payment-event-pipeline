package com.team.pipeline.application.risk

final case class RiskPolicy(
    reviewThreshold: Int,
    blockThreshold: Int,
    newAccountHours: Long,
    highAmountDailyLimitRatio: BigDecimal,
    velocityWindowMinutes: Long,
    velocityTransactionThreshold: Int,
    failedAttemptThreshold: Int,
    cumulativeSpendWindowHours: Long,
    lateNightStartHour: Int,
    lateNightEndHour: Int,
    lateNightWindowDays: Long,
    lateNightThreshold: Int,
    amountOutlierMinHistory: Int,
    amountOutlierStddevMultiplier: BigDecimal
)

object RiskPolicy:
  val default: RiskPolicy = RiskPolicy(
    reviewThreshold = 20,
    blockThreshold = 80,
    newAccountHours = 48,
    highAmountDailyLimitRatio = BigDecimal("0.80"),
    velocityWindowMinutes = 60,
    velocityTransactionThreshold = 5,
    failedAttemptThreshold = 3,
    cumulativeSpendWindowHours = 24,
    lateNightStartHour = 0,
    lateNightEndHour = 5,
    lateNightWindowDays = 7,
    lateNightThreshold = 3,
    amountOutlierMinHistory = 5,
    amountOutlierStddevMultiplier = BigDecimal("3.0")
  )
