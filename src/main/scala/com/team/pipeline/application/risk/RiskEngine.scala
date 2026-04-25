package com.team.pipeline.application.risk

import com.team.pipeline.domain.AlertType
import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.RiskAssessment
import com.team.pipeline.domain.RiskDecision

import java.time.Duration
import java.time.ZoneOffset

object RiskEngine:
  def evaluate(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext,
      policy: RiskPolicy
  ): RiskAssessment =
    given RiskPolicy = policy

    val alerts = List(
      previouslyFlaggedCustomer(event),
      countryMismatch(event),
      newAccountHighAmount(event),
      lateNightTransaction(event),
      velocitySpike(event, context),
      failedAttemptBurst(event, context),
      repeatedLateNightActivity(event, context),
      newDeviceHighRisk(event, context),
      amountOutlier(event, context),
      seniorMethodShiftAnomaly(event, context)
    ).flatten
    val riskScore = alerts.map(_.riskScore).sum

    RiskAssessment(
      riskScore = riskScore,
      decision = decisionForScore(riskScore),
      alerts = alerts
    )

  private[risk] def decisionForScore(
      riskScore: Int
  )(using policy: RiskPolicy): RiskDecision =
    if riskScore >= policy.blockThreshold then RiskDecision.Block
    else if riskScore >= policy.reviewThreshold then RiskDecision.Review
    else RiskDecision.Approve

  private def previouslyFlaggedCustomer(event: EnrichedPaymentEvent): Option[Alert] =
    Option.when(event.customer.fraudBefore)(
      alert(event, AlertType.PreviouslyFlaggedCustomer, 20, "Customer has previous fraud flag")
    )

  private def countryMismatch(event: EnrichedPaymentEvent): Option[Alert] =
    Option.when(hasCountryMismatch(event))(
      alert(
        event,
        AlertType.CountryMismatch,
        15,
        s"Transaction country ${event.event.transactionCountry} does not match known customer countries"
      )
    )

  private def newAccountHighAmount(
      event: EnrichedPaymentEvent
  )(using policy: RiskPolicy): Option[Alert] =
    val accountAgeHours =
      Duration.between(event.customer.createdAt, event.event.timestamp).toHours

    Option.when(
      accountAgeHours >= 0 &&
        accountAgeHours < policy.newAccountHours &&
        isHighAmount(event)
    )(
      alert(
        event,
        AlertType.NewAccountHighAmount,
        20,
        s"New account transaction amount ${event.event.amount} is close to daily limit ${event.customer.dailyLimit}"
      )
    )

  private def lateNightTransaction(
      event: EnrichedPaymentEvent
  )(using policy: RiskPolicy): Option[Alert] =
    Option.when(isLateNight(event))(
      alert(
        event,
        AlertType.LateNightTransaction,
        10,
        s"Transaction happened during late-night window ${policy.lateNightStartHour}:00-${policy.lateNightEndHour}:00 UTC"
      )
    )

  private def velocitySpike(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext
  )(using policy: RiskPolicy): Option[Alert] =
    val transactionCountWithCurrent = context.transactionCountLastHour + 1

    Option.when(transactionCountWithCurrent >= policy.velocityTransactionThreshold)(
      alert(
        event,
        AlertType.VelocitySpike,
        30,
        s"Customer reached $transactionCountWithCurrent transactions in the recent velocity window"
      )
    )

  private def failedAttemptBurst(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext
  )(using policy: RiskPolicy): Option[Alert] =
    val currentFailed = if event.event.status == EventStatus.Failed then 1 else 0
    val failedAttemptsWithCurrent = context.failedAttemptCountLastHour + currentFailed

    Option.when(failedAttemptsWithCurrent >= policy.failedAttemptThreshold)(
      alert(
        event,
        AlertType.FailedAttemptBurst,
        30,
        s"Customer reached $failedAttemptsWithCurrent failed attempts in the recent failure window"
      )
    )

  private def repeatedLateNightActivity(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext
  )(using policy: RiskPolicy): Option[Alert] =
    val isLateNightEvent = isLateNight(event)
    val lateNightCountWithCurrent =
      context.lateNightTransactionCountLast7d + (if isLateNightEvent then 1 else 0)

    Option.when(
      isLateNightEvent &&
        lateNightCountWithCurrent >= policy.lateNightThreshold
    )(
      alert(
        event,
        AlertType.RepeatedLateNightActivity,
        20,
        s"Customer reached $lateNightCountWithCurrent late-night transactions in the recent night window"
      )
    )

  private def newDeviceHighRisk(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext
  )(using policy: RiskPolicy): Option[Alert] =
    Option.when(!context.knownDevice && (isHighAmount(event) || hasCountryMismatch(event)))(
      alert(
        event,
        AlertType.NewDeviceHighRisk,
        20,
        "Unknown device combined with high amount or country mismatch"
      )
    )

  private def amountOutlier(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext
  )(using policy: RiskPolicy): Option[Alert] =
    val outlierThreshold =
      for
        average <- context.averageAmount30d
        stddev <- context.amountStddev30d
        if context.historySize30d >= policy.amountOutlierMinHistory
        if stddev > 0
      yield average + stddev * policy.amountOutlierStddevMultiplier

    Option.when(outlierThreshold.exists(event.event.amount > _))(
      alert(
        event,
        AlertType.AmountOutlier,
        25,
        "Transaction amount is unusual compared with recent customer history"
      )
    )

  private def seniorMethodShiftAnomaly(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext
  )(using policy: RiskPolicy): Option[Alert] =
    val isSenior = event.customer.age > policy.seniorAgeThreshold
    val isMethodTracked =
      Set(PaymentMethod.Blik, PaymentMethod.Transfer).contains(event.event.paymentMethod)
    val hasHistory = context.totalTransactionCountLast30d >= policy.seniorMethodShiftMinHistory

    val baselineDaily = BigDecimal(context.blikTransferCountLast30d) / BigDecimal(30)
    val recentCount = BigDecimal(context.blikTransferCountLast24h)
    val spikeByMultiplier =
      baselineDaily > 0 && recentCount >= baselineDaily * policy.seniorMethodShiftMultiplier
    val spikeFromZeroBaseline =
      baselineDaily == 0 && recentCount >= BigDecimal(policy.seniorMethodShiftMinRecentCount)

    Option.when(
      isSenior &&
        isMethodTracked &&
        hasHistory &&
        (spikeByMultiplier || spikeFromZeroBaseline)
    )(
      alert(
        event,
        AlertType.SeniorMethodShiftAnomaly,
        policy.seniorMethodShiftScore,
        s"Senior customer has abrupt BLIK/Transfer activity spike: recent24h=$recentCount baselineDaily=$baselineDaily"
      )
    )

  private def hasCountryMismatch(event: EnrichedPaymentEvent): Boolean =
    val knownCountries = Set(
      normalizeCountry(event.customer.country),
      normalizeCountry(event.customer.lastLoginCountry)
    )
    !knownCountries.contains(normalizeCountry(event.event.transactionCountry))

  private def normalizeCountry(value: String): String =
    value.trim.toUpperCase

  private def isHighAmount(
      event: EnrichedPaymentEvent
  )(using policy: RiskPolicy): Boolean =
    val highAmountThreshold =
      event.customer.dailyLimit * policy.highAmountDailyLimitRatio
    event.event.amount >= highAmountThreshold

  private def isLateNight(
      event: EnrichedPaymentEvent
  )(using policy: RiskPolicy): Boolean =
    val hour = event.event.timestamp.atZone(ZoneOffset.UTC).getHour
    isInHourWindow(hour, policy.lateNightStartHour, policy.lateNightEndHour)

  private def isInHourWindow(hour: Int, startHour: Int, endHour: Int): Boolean =
    if startHour <= endHour then hour >= startHour && hour < endHour
    else hour >= startHour || hour < endHour

  private def alert(
      event: EnrichedPaymentEvent,
      alertType: AlertType,
      riskScore: Int,
      message: String
  ): Alert =
    Alert(
      alertType = alertType,
      eventId = event.event.eventId,
      customerId = event.event.customerId,
      message = message,
      riskScore = riskScore
    )
