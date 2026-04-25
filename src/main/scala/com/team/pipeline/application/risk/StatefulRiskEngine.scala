package com.team.pipeline.application.risk

import com.team.pipeline.domain.{Alert, AlertType, EnrichedPaymentEvent, RiskAssessment}

import java.time.{Duration, Instant, ZoneOffset}

/** Stateful / window-based fraud rules.
  *
  * This module is still deterministic and test-friendly: the state is an immutable value that the
  * caller threads through the stream (e.g. via `fold`).
  */
object StatefulRiskEngine:

  final case class Config(
      window24h: Duration = Duration.ofHours(24),
      window1h: Duration = Duration.ofHours(1),
      nightWindow: Duration = Duration.ofHours(5),
      nightStartHourUtcInclusive: Int = 0,
      nightEndHourUtcExclusive: Int = 5,
      velocityThreshold: Int = 10,
      nightBurstThreshold: Int = 3,
      zScoreMinSamples: Int = 20,
      zScoreThreshold: Double = 3.0,
      iqrMinSamples: Int = 20,
      maxStoredAmounts: Int = 200
  )

  val defaultConfig: Config = Config()

  final case class RiskState(byCustomer: Map[Int, CustomerState]):
    def next(
        event: EnrichedPaymentEvent,
        config: Config = defaultConfig,
        baseConfig: RiskEngine.Config = RiskEngine.defaultConfig
    ): (RiskState, RiskAssessment) =
      val base = RiskEngine.evaluate(event, baseConfig)

      val customerId = event.customer.customerId
      val currentTs = event.event.timestamp
      val currentAmount = event.event.amount.toDouble

      val existing = byCustomer.getOrElse(customerId, CustomerState.empty)
      val pruned = existing.prune(currentTs, config.window24h)

      // Evaluate stateful rules using history *before* adding the current event to stats.
      val statefulAlerts = List(
        rolling24hLimitExceeded(event, pruned, config),
        velocitySpike(event, pruned, config),
        nightBurstNearLimit(event, pruned, config, baseConfig),
        zScoreOutlier(event, pruned, config),
        iqrOutlier(event, pruned, config)
      ).flatten

      val allAlerts = base.alerts ++ statefulAlerts
      val assessment = RiskAssessment(
        riskScore = allAlerts.map(_.riskScore).sum,
        alerts = allAlerts
      )

      val updated = pruned.add(currentTs, currentAmount, config)
      val newState = copy(byCustomer = byCustomer.updated(customerId, updated))
      (newState, assessment)

  object RiskState:
    val empty: RiskState = RiskState(Map.empty)

  final case class TxRecord(timestamp: Instant, amount: Double)

  final case class RunningStats(count: Long, mean: Double, m2: Double):
    def add(x: Double): RunningStats =
      val newCount = count + 1
      val delta = x - mean
      val newMean = mean + (delta / newCount)
      val delta2 = x - newMean
      val newM2 = m2 + (delta * delta2)
      RunningStats(newCount, newMean, newM2)

    def stddev: Option[Double] =
      if count >= 2 then
        val variance = m2 / (count - 1)
        Option.when(variance > 0)(math.sqrt(variance))
      else None

  object RunningStats:
    val empty: RunningStats = RunningStats(0L, 0.0, 0.0)

  final case class CustomerState(
      recent24h: Vector[TxRecord],
      stats: RunningStats,
      lastAmounts: Vector[Double]
  ):

    def prune(now: Instant, window24h: Duration): CustomerState =
      val cutoff = now.minus(window24h)
      copy(recent24h = recent24h.filter(_.timestamp.isAfter(cutoff)))

    def add(now: Instant, amount: Double, config: Config): CustomerState =
      val nextRecent = (recent24h :+ TxRecord(now, amount)).takeRight(10_000)
      val nextStats = stats.add(amount)
      val nextAmounts = (lastAmounts :+ amount).takeRight(config.maxStoredAmounts)
      copy(recent24h = nextRecent, stats = nextStats, lastAmounts = nextAmounts)

  object CustomerState:
    val empty: CustomerState = CustomerState(Vector.empty, RunningStats.empty, Vector.empty)

  private def rolling24hLimitExceeded(
      event: EnrichedPaymentEvent,
      state: CustomerState,
      config: Config
  ): Option[Alert] =
    val limit = event.customer.dailyLimit
    if limit <= 0 then None
    else
      val sum24h = state.recent24h.map(_.amount).sum + event.event.amount.toDouble
      Option.when(BigDecimal(sum24h) > limit)(
        mkAlert(
          AlertType.Rolling24hLimitExceeded,
          event,
          s"Rolling 24h spend (${fmt(sum24h)}) exceeds daily limit ($limit)."
        )
      )

  private def velocitySpike(
      event: EnrichedPaymentEvent,
      state: CustomerState,
      config: Config
  ): Option[Alert] =
    val now = event.event.timestamp
    val cutoff = now.minus(config.window1h)
    val count = state.recent24h.count(_.timestamp.isAfter(cutoff)) + 1

    Option.when(count >= config.velocityThreshold)(
      mkAlert(
        AlertType.VelocitySpike,
        event,
        s"High velocity: $count transactions in the last hour (threshold=${config.velocityThreshold})."
      )
    )

  private def nightBurstNearLimit(
      event: EnrichedPaymentEvent,
      state: CustomerState,
      config: Config,
      baseConfig: RiskEngine.Config
  ): Option[Alert] =
    val now = event.event.timestamp
    val currentHour = now.atOffset(ZoneOffset.UTC).getHour
    val isNight =
      currentHour >= config.nightStartHourUtcInclusive && currentHour < config.nightEndHourUtcExclusive

    if !isNight then None
    else
      val cutoff = now.minus(config.nightWindow)
      val nightCount =
        state.recent24h
          .filter(_.timestamp.isAfter(cutoff))
          .count(r =>
            val h = r.timestamp.atOffset(ZoneOffset.UTC).getHour
            h >= config.nightStartHourUtcInclusive && h < config.nightEndHourUtcExclusive
          ) + 1

      val nearLimit =
        event.customer.dailyLimit > 0 &&
          event.event.amount >= (event.customer.dailyLimit * baseConfig.nearLimitRatio)

      Option.when(nightCount >= config.nightBurstThreshold && nearLimit)(
        mkAlert(
          AlertType.NightBurstNearLimit,
          event,
          s"Night burst: $nightCount transactions within ${config.nightWindow.toHours}h during night window, near-limit transaction."
        )
      )

  private def zScoreOutlier(
      event: EnrichedPaymentEvent,
      state: CustomerState,
      config: Config
  ): Option[Alert] =
    val n = state.stats.count
    if n < config.zScoreMinSamples then None
    else
      state.stats.stddev.flatMap { sd =>
        val mean = state.stats.mean
        val x = event.event.amount.toDouble
        val z = (x - mean) / sd
        Option.when(z > config.zScoreThreshold)(
          mkAlert(
            AlertType.ZScoreAmountOutlier,
            event,
            f"Amount outlier by z-score: z=$z%.2f (mean=$mean%.2f, sd=$sd%.2f)."
          )
        )
      }

  private def iqrOutlier(
      event: EnrichedPaymentEvent,
      state: CustomerState,
      config: Config
  ): Option[Alert] =
    val amounts = state.lastAmounts
    if amounts.size < config.iqrMinSamples then None
    else
      val sorted = amounts.sorted
      val q1 = percentile(sorted, 0.25)
      val q3 = percentile(sorted, 0.75)
      val iqr = q3 - q1
      if iqr <= 0 then None
      else
        val upperFence = q3 + (1.5 * iqr)
        val x = event.event.amount.toDouble
        Option.when(x > upperFence)(
          mkAlert(
            AlertType.IqrAmountOutlier,
            event,
            f"Amount outlier by IQR: amount=${x}%.2f, Q1=$q1%.2f, Q3=$q3%.2f, upperFence=$upperFence%.2f."
          )
        )

  private def percentile(sorted: Vector[Double], p: Double): Double =
    val n = sorted.size
    if n == 0 then 0.0
    else
      val pos = p * (n - 1)
      val lower = math.floor(pos).toInt
      val upper = math.ceil(pos).toInt
      if lower == upper then sorted(lower)
      else
        val weight = pos - lower
        sorted(lower) + (sorted(upper) - sorted(lower)) * weight

  private def mkAlert(alertType: AlertType, event: EnrichedPaymentEvent, message: String): Alert =
    val score = RiskEngine.weights.getOrElse(alertType, 0)
    Alert(
      alertType = alertType,
      eventId = event.event.eventId,
      customerId = event.customer.customerId,
      message = message,
      riskScore = score
    )

  private def fmt(x: Double): String =
    f"$x%.2f"
