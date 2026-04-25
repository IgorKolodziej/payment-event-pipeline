package com.team.pipeline.application.risk

final case class CustomerRiskContext(
    transactionCountLastHour: Int,
    failedAttemptCountLastHour: Int,
    approvedAmountLast24h: BigDecimal,
    lateNightTransactionCountLast7d: Int,
    knownDevice: Boolean,
    averageAmount30d: Option[BigDecimal],
    amountStddev30d: Option[BigDecimal],
    historySize30d: Int
)
