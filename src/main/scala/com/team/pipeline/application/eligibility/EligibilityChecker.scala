package com.team.pipeline.application.eligibility

import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.domain.EligibilityAssessment
import com.team.pipeline.domain.EligibilityDecision
import com.team.pipeline.domain.EligibilityViolation
import com.team.pipeline.domain.EligibilityViolationType
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus

object EligibilityChecker:
  def evaluate(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext
  ): EligibilityAssessment =
    val violations = List(
      inactiveCustomer(event),
      insufficientBalance(event),
      singleTransactionLimitExceeded(event),
      dailyLimitExceeded(event, context),
      paymentMethodNotAllowed(event)
    ).flatten

    EligibilityAssessment(
      decision =
        if violations.isEmpty then EligibilityDecision.Eligible
        else EligibilityDecision.Declined,
      violations = violations
    )

  private def inactiveCustomer(event: EnrichedPaymentEvent): Option[EligibilityViolation] =
    Option.when(!event.customer.isActive)(
      violation(
        event,
        EligibilityViolationType.InactiveCustomer,
        "Customer account is inactive"
      )
    )

  private def insufficientBalance(event: EnrichedPaymentEvent): Option[EligibilityViolation] =
    Option.when(
      event.event.status == EventStatus.Success &&
        event.event.amount > event.customer.balance
    )(
      violation(
        event,
        EligibilityViolationType.InsufficientBalance,
        s"Amount ${event.event.amount} exceeds available balance ${event.customer.balance}"
      )
    )

  private def singleTransactionLimitExceeded(
      event: EnrichedPaymentEvent
  ): Option[EligibilityViolation] =
    Option.when(
      event.event.status == EventStatus.Success &&
        event.event.amount > event.customer.dailyLimit
    )(
      violation(
        event,
        EligibilityViolationType.SingleTransactionLimitExceeded,
        s"Amount ${event.event.amount} exceeds daily limit ${event.customer.dailyLimit}"
      )
    )

  private def dailyLimitExceeded(
      event: EnrichedPaymentEvent,
      context: CustomerRiskContext
  ): Option[EligibilityViolation] =
    val amountWithCurrent = context.approvedAmountLast24h + event.event.amount

    Option.when(
      event.event.status == EventStatus.Success &&
        event.event.amount <= event.customer.dailyLimit &&
        amountWithCurrent > event.customer.dailyLimit
    )(
      violation(
        event,
        EligibilityViolationType.DailyLimitExceeded,
        s"Recent approved amount plus current transaction exceeds daily limit ${event.customer.dailyLimit}"
      )
    )

  private def paymentMethodNotAllowed(event: EnrichedPaymentEvent): Option[EligibilityViolation] =
    Option.when(!event.customer.allowedPaymentMethods.contains(event.event.paymentMethod))(
      violation(
        event,
        EligibilityViolationType.PaymentMethodNotAllowed,
        s"Payment method ${event.event.paymentMethod} is not enabled for customer"
      )
    )

  private def violation(
      event: EnrichedPaymentEvent,
      violationType: EligibilityViolationType,
      message: String
  ): EligibilityViolation =
    EligibilityViolation(
      violationType = violationType,
      eventId = event.event.eventId,
      customerId = event.event.customerId,
      message = message
    )
