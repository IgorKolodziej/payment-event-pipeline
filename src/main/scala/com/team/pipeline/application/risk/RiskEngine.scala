package com.team.pipeline.application.risk

import com.team.pipeline.domain.AlertType
import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
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
    val alerts = List(
      inactiveCustomer(event),
      limitExceeded(event),
      cumulativeLimitExceeded(event, context),
      invalidPaymentMethod(event),
      previouslyFlaggedCustomer(event),
      countryMismatch(event),
      newAccountHighAmount(event, policy),
      lateNightTransaction(event, policy),
      velocitySpike(event, context, policy),
      failedAttemptBurst(event, context, policy),
      repeatedLateNightActivity(event, context, policy),
      newDeviceHighRisk(event, context, policy),
      amountOutlier(event, context, policy)
    ).flatten
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

  private def inactiveCustomer(event: EnrichedPaymentEvent): Option[Alert] =
    Option.when(!event.customer.isActive)(
      alert(event, AlertType.InactiveCustomer, 50, "Customer account is inactive")
    )

  private def limitExceeded(event: EnrichedPaymentEvent): Option[Alert] =
    Option.when(event.event.amount > event.customer.dailyLimit)(
      alert(
        event,
        AlertType.LimitExceeded,
        30,
        s"Amount ${event.event.amount} exceeds daily limit ${event.customer.dailyLimit}"
      )
    )

  private def cumulativeLimitExceeded(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext
  ): Option[Alert] =
    val amountWithCurrent = context.approvedAmountLast24h + event.event.amount

    Option.when(
      event.event.status == EventStatus.Success &&
        event.event.amount <= event.customer.dailyLimit &&
        amountWithCurrent > event.customer.dailyLimit
    )(
      alert(
        event,
        AlertType.CumulativeLimitExceeded,
        35,
        s"Recent approved amount plus current transaction exceeds daily limit ${event.customer.dailyLimit}"
      )
    )

  private def invalidPaymentMethod(event: EnrichedPaymentEvent): Option[Alert] =
    Option.when(!event.customer.allowedPaymentMethods.contains(event.event.paymentMethod))(
      alert(
        event,
        AlertType.InvalidPaymentMethod,
        25,
        s"Payment method ${event.event.paymentMethod} is not enabled for customer"
      )
    )

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
      event: EnrichedPaymentEvent,
      policy: RiskPolicy
  ): Option[Alert] =
    val accountAgeHours =
      Duration.between(event.customer.createdAt, event.event.timestamp).toHours

    Option.when(
      accountAgeHours >= 0 &&
        accountAgeHours < policy.newAccountHours &&
        isHighAmount(event, policy)
    )(
      alert(
        event,
        AlertType.NewAccountHighAmount,
        20,
        s"New account transaction amount ${event.event.amount} is close to daily limit ${event.customer.dailyLimit}"
      )
    )

  private def lateNightTransaction(
      event: EnrichedPaymentEvent,
      policy: RiskPolicy
  ): Option[Alert] =
    Option.when(isLateNight(event, policy))(
      alert(
        event,
        AlertType.LateNightTransaction,
        10,
        s"Transaction happened during late-night window ${policy.lateNightStartHour}:00-${policy.lateNightEndHour}:00 UTC"
      )
    )

  private def velocitySpike(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext,
      policy: RiskPolicy
  ): Option[Alert] =
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
      context: CustomerRiskContext,
      policy: RiskPolicy
  ): Option[Alert] =
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
      context: CustomerRiskContext,
      policy: RiskPolicy
  ): Option[Alert] =
    val isLateNightEvent = isLateNight(event, policy)
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
      context: CustomerRiskContext,
      policy: RiskPolicy
  ): Option[Alert] =
    Option.when(!context.knownDevice && (isHighAmount(event, policy) || hasCountryMismatch(event)))(
      alert(
        event,
        AlertType.NewDeviceHighRisk,
        20,
        "Unknown device combined with high amount or country mismatch"
      )
    )

  private def amountOutlier(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext,
      policy: RiskPolicy
  ): Option[Alert] =
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

  private def hasCountryMismatch(event: EnrichedPaymentEvent): Boolean =
    val knownCountries = Set(
      normalizeCountry(event.customer.country),
      normalizeCountry(event.customer.lastLoginCountry)
    )
    !knownCountries.contains(normalizeCountry(event.event.transactionCountry))

  private def normalizeCountry(value: String): String =
    value.trim.toUpperCase

  private def isHighAmount(
      event: EnrichedPaymentEvent,
      policy: RiskPolicy
  ): Boolean =
    val highAmountThreshold =
      event.customer.dailyLimit * policy.highAmountDailyLimitRatio
    event.event.amount >= highAmountThreshold

  private def isLateNight(
      event: EnrichedPaymentEvent,
      policy: RiskPolicy
  ): Boolean =
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
