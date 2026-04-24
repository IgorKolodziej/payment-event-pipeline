package com.team.pipeline.application.risk

import java.time.Instant

final case class CustomerRiskContext(
    transactionCountLastHour: Int,
    failedAttemptCountLastHour: Int,
    approvedAmountLast24h: BigDecimal,
    lateNightTransactionCountLast7d: Int,
    knownDevice: Boolean,
    firstSeenDeviceAt: Option[Instant],
    averageAmount30d: Option[BigDecimal],
    amountStddev30d: Option[BigDecimal],
    historySize30d: Int
)
