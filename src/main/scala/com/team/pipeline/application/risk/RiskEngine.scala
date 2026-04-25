package com.team.pipeline.application.risk

import com.team.pipeline.domain.{
  Alert,
  AlertType,
  EnrichedPaymentEvent,
  PaymentMethod,
  RiskAssessment
}

import java.time.{Duration, Instant, ZoneOffset}

/** Pure, deterministic risk evaluation.
  *
  * This object contains stateless rules that depend only on the current enriched event.
  * Stateful / window-based rules live in `StatefulRiskEngine`.
  */
object RiskEngine:

  final case class Config(
      nearLimitRatio: BigDecimal = BigDecimal("0.80"),
      newAccountWindow: Duration = Duration.ofHours(48)
  )

  val defaultConfig: Config = Config()

  val weights: Map[AlertType, Int] = Map(
    AlertType.InactiveCustomer -> 50,
    AlertType.LimitExceeded -> 30,
    AlertType.InvalidPaymentMethod -> 25,
    AlertType.PreviouslyFlaggedCustomer -> 20,
    AlertType.CountryLoginMismatch -> 15,
    AlertType.NewAccountNearLimit -> 20,
    AlertType.SeniorNearLimitHighRiskMethod -> 15,
    // stateful rules also use this table
    AlertType.Rolling24hLimitExceeded -> 35,
    AlertType.VelocitySpike -> 40,
    AlertType.NightBurstNearLimit -> 20,
    AlertType.ZScoreAmountOutlier -> 25,
    AlertType.IqrAmountOutlier -> 25
  )

  def evaluate(event: EnrichedPaymentEvent, config: Config = defaultConfig): RiskAssessment =
    val e = event.event
    val c = event.customer

    val alerts = List(
      inactiveCustomer(event),
      limitExceeded(event),
      invalidPaymentMethod(event),
      previouslyFlaggedCustomer(event),
      countryLoginMismatch(event),
      newAccountNearLimit(event, config),
      seniorNearLimitHighRiskMethod(event, config)
    ).flatten

    val riskScore = alerts.map(_.riskScore).sum
    RiskAssessment(riskScore = riskScore, alerts = alerts)

  private def inactiveCustomer(event: EnrichedPaymentEvent): Option[Alert] =
    Option.when(!event.customer.isActive)(
      mkAlert(
        AlertType.InactiveCustomer,
        event,
        s"Customer is inactive (isActive=false)."
      )
    )

  private def limitExceeded(event: EnrichedPaymentEvent): Option[Alert] =
    val amount = event.event.amount
    val limit = event.customer.dailyLimit

    Option.when(amount > limit)(
      mkAlert(
        AlertType.LimitExceeded,
        event,
        s"Event amount ($amount) exceeds customer daily limit ($limit)."
      )
    )

  private def invalidPaymentMethod(event: EnrichedPaymentEvent): Option[Alert] =
    val eventMethods = event.event.paymentMethods
    val allowed = event.customer.allowedPaymentMethods

    val invalid = eventMethods.diff(allowed)

    Option.when(invalid.nonEmpty)(
      mkAlert(
        AlertType.InvalidPaymentMethod,
        event,
        s"Event used payment method(s) not enabled for customer: ${invalid.toList.sorted.mkString(", ")}."
      )
    )

  private def previouslyFlaggedCustomer(event: EnrichedPaymentEvent): Option[Alert] =
    Option.when(event.customer.fraudBefore)(
      mkAlert(
        AlertType.PreviouslyFlaggedCustomer,
        event,
        s"Customer is marked as previously flagged (fraudBefore=true)."
      )
    )

  private def countryLoginMismatch(event: EnrichedPaymentEvent): Option[Alert] =
    val country = event.customer.country
    val lastLogin = event.customer.lastLoginCountry

    Option.when(country != lastLogin)(
      mkAlert(
        AlertType.CountryLoginMismatch,
        event,
        s"Customer country ($country) differs from last login country ($lastLogin)."
      )
    )

  private def newAccountNearLimit(
      event: EnrichedPaymentEvent,
      config: Config
  ): Option[Alert] =
    val createdAt = event.customer.createdAt

    createdAt.flatMap { created =>
      val age = Duration.between(created, event.event.timestamp)
      val isNew = !age.isNegative && age.compareTo(config.newAccountWindow) < 0
      val isNearLimit = nearLimit(event.event.amount, event.customer.dailyLimit, config.nearLimitRatio)

      Option.when(isNew && isNearLimit)(
        mkAlert(
          AlertType.NewAccountNearLimit,
          event,
          s"New account (${age.toHours}h old) performing near-limit transaction."
        )
      )
    }

  private def seniorNearLimitHighRiskMethod(
      event: EnrichedPaymentEvent,
      config: Config
  ): Option[Alert] =
    val isSenior = event.customer.age > 70
    val methods = event.event.paymentMethods
    val usesHighRisk = methods.contains(PaymentMethod.Blik) || methods.contains(PaymentMethod.Transfer)
    val isNearLimit = nearLimit(event.event.amount, event.customer.dailyLimit, config.nearLimitRatio)

    Option.when(isSenior && usesHighRisk && isNearLimit)(
      mkAlert(
        AlertType.SeniorNearLimitHighRiskMethod,
        event,
        s"Senior customer (age=${event.customer.age}) used BLIK/Transfer for near-limit transaction."
      )
    )

  private def nearLimit(amount: BigDecimal, limit: BigDecimal, ratio: BigDecimal): Boolean =
    limit > 0 && amount >= (limit * ratio)

  private def mkAlert(alertType: AlertType, event: EnrichedPaymentEvent, message: String): Alert =
    val score = weights.getOrElse(alertType, 0)
    Alert(
      alertType = alertType,
      eventId = event.event.eventId,
      customerId = event.customer.customerId,
      message = message,
      riskScore = score
    )

  private given Ordering[PaymentMethod] with
    override def compare(x: PaymentMethod, y: PaymentMethod): Int =
      x.toString.compareTo(y.toString)

  private def hourUtc(ts: Instant): Int =
    ts.atOffset(ZoneOffset.UTC).getHour
