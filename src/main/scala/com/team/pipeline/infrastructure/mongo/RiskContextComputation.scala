package com.team.pipeline.infrastructure.mongo

import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.application.risk.RiskPolicy
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.PaymentMethod

import java.math.MathContext
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

object RiskContextComputation:

  final case class HistoryEvent(
      eventId: Int,
      timestamp: Instant,
      amount: BigDecimal,
      status: EventStatus,
      paymentMethod: PaymentMethod,
      deviceId: String,
      finalDecision: FinalDecision
  )

  private val StddevMathContext: MathContext =
    MathContext(16, RoundingMode.HALF_UP)

  def compute(
      current: EnrichedPaymentEvent,
      rawHistory: List[HistoryEvent]
  ): CustomerRiskContext =
    val currentEventId = current.event.eventId
    val now = current.event.timestamp

    val history = rawHistory.filter(h => h.eventId != currentEventId && h.timestamp.isBefore(now))

    val lastHourFrom = now.minus(Duration.ofHours(1))
    val last24hFrom = now.minus(Duration.ofHours(24))
    val last7dFrom = now.minus(Duration.ofDays(7))
    val last30dFrom = now.minus(Duration.ofDays(30))

    val lastHour = history.filter(h => inWindow(h.timestamp, lastHourFrom, now))
    val last24h = history.filter(h => inWindow(h.timestamp, last24hFrom, now))
    val last7d = history.filter(h => inWindow(h.timestamp, last7dFrom, now))
    val last30d = history.filter(h => inWindow(h.timestamp, last30dFrom, now))

    val transactionCountLastHour = lastHour.size
    val failedAttemptCountLastHour = lastHour.count(_.status == EventStatus.Failed)

    val approvedAmountLast24h =
      last24h
        .filter(h => h.status == EventStatus.Success && h.finalDecision == FinalDecision.Accepted)
        .map(_.amount)
        .foldLeft(BigDecimal(0))(_ + _)

    val lateNightTransactionCountLast7d =
      val policy = RiskPolicy.default
      last7d.count(h => isLateNight(h.timestamp, policy.lateNightStartHour, policy.lateNightEndHour))

    val knownDevice =
      val deviceId = current.event.deviceId
      history.exists(_.deviceId == deviceId)

    val acceptedSuccessAmounts30d =
      last30d
        .filter(h => h.status == EventStatus.Success && h.finalDecision == FinalDecision.Accepted)
        .map(_.amount)

    val (averageAmount30d, amountStddev30d, historySize30d) =
      if acceptedSuccessAmounts30d.isEmpty then (None, None, 0)
      else
        val n = acceptedSuccessAmounts30d.size
        val mean = acceptedSuccessAmounts30d.sum / BigDecimal(n)
        val variance =
          acceptedSuccessAmounts30d
            .map(a => (a - mean).pow(2))
            .sum / BigDecimal(n)

        val stddev = BigDecimal(variance.bigDecimal.sqrt(StddevMathContext))

        (Some(mean), Some(stddev), n)

    val blikTransferCountLast24h =
      last24h.count(h => isBlikOrTransfer(h.paymentMethod))

    val blikTransferCountLast30d =
      last30d.count(h => isBlikOrTransfer(h.paymentMethod))

    val totalTransactionCountLast30d = last30d.size

    CustomerRiskContext(
      transactionCountLastHour = transactionCountLastHour,
      failedAttemptCountLastHour = failedAttemptCountLastHour,
      approvedAmountLast24h = approvedAmountLast24h,
      lateNightTransactionCountLast7d = lateNightTransactionCountLast7d,
      knownDevice = knownDevice,
      averageAmount30d = averageAmount30d,
      amountStddev30d = amountStddev30d,
      historySize30d = historySize30d,
      blikTransferCountLast24h = blikTransferCountLast24h,
      blikTransferCountLast30d = blikTransferCountLast30d,
      totalTransactionCountLast30d = totalTransactionCountLast30d
    )

  private def inWindow(instant: Instant, from: Instant, toExclusive: Instant): Boolean =
    !instant.isBefore(from) && instant.isBefore(toExclusive)

  private def isLateNight(timestamp: Instant, startHour: Int, endHour: Int): Boolean =
    val hour = timestamp.atZone(ZoneOffset.UTC).getHour
    if startHour <= endHour then hour >= startHour && hour < endHour
    else hour >= startHour || hour < endHour

  private def isBlikOrTransfer(method: PaymentMethod): Boolean =
    method == PaymentMethod.Blik || method == PaymentMethod.Transfer
