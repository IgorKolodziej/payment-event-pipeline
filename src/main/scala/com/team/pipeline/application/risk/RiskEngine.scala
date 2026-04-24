package com.team.pipeline.application.risk

import com.team.pipeline.domain.AlertType
import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.EnrichedPaymentEvent
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
      invalidPaymentMethod(event),
      previouslyFlaggedCustomer(event),
      countryMismatch(event),
      newAccountHighAmount(event, policy),
      lateNightTransaction(event, policy)
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
    val knownCountries = Set(event.customer.country, event.customer.lastLoginCountry)

    Option.when(!knownCountries.contains(event.event.transactionCountry))(
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
    val highAmountThreshold =
      event.customer.dailyLimit * policy.highAmountDailyLimitRatio

    Option.when(
      accountAgeHours >= 0 &&
        accountAgeHours < policy.newAccountHours &&
        event.event.amount >= highAmountThreshold
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
    val hour = event.event.timestamp.atZone(ZoneOffset.UTC).getHour

    Option.when(isInHourWindow(hour, policy.lateNightStartHour, policy.lateNightEndHour))(
      alert(
        event,
        AlertType.LateNightTransaction,
        10,
        s"Transaction happened during late-night window ${policy.lateNightStartHour}:00-${policy.lateNightEndHour}:00 UTC"
      )
    )

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
