package com.team.pipeline.application.risk

import com.team.pipeline.domain.*
import com.team.pipeline.application.risk.StatefulRiskEngine.*
import munit.FunSuite

import java.time.{Duration, Instant}

class StatefulRiskEngineTest extends FunSuite:

  private val baseCustomer: CustomerProfile = CustomerProfile(
    customerId = 1,
    firstName = "A",
    lastName = "B",
    email = "a@b.com",
    country = "PL",
    balance = BigDecimal(0),
    dailyLimit = BigDecimal(1000),
    allowedPaymentMethods = Set(PaymentMethod.Blik, PaymentMethod.Card, PaymentMethod.Transfer),
    isActive = true,
    age = 30,
    gender = "X",
    lastLoginCountry = "PL",
    fraudBefore = false
  )

  private def enriched(
      eventId: Int,
      ts: Instant,
      amount: BigDecimal,
      methods: Set[PaymentMethod] = Set(PaymentMethod.Card),
      customer: CustomerProfile = baseCustomer
  ): EnrichedPaymentEvent =
    EnrichedPaymentEvent(
      event = NormalizedPaymentEvent(
        eventId = eventId,
        timestamp = ts,
        customerId = customer.customerId,
        amount = amount,
        status = EventStatus.Success,
        paymentMethods = methods
      ),
      customer = customer,
      hashedCustomerEmail = "hash"
    )

  test("rolling 24h sum can exceed limit even if single event does not"):
    val start = Instant.parse("2026-04-24T10:00:00Z")

    val (s1, a1) = RiskState.empty.next(enriched(1, start, 400))
    assert(!a1.alerts.exists(_.alertType == AlertType.Rolling24hLimitExceeded))

    val (s2, a2) = s1.next(enriched(2, start.plusSeconds(60), 400))
    assert(!a2.alerts.exists(_.alertType == AlertType.Rolling24hLimitExceeded))

    val (_, a3) = s2.next(enriched(3, start.plusSeconds(120), 400))
    assert(a3.alerts.exists(_.alertType == AlertType.Rolling24hLimitExceeded))

  test("velocity spike triggers at configured threshold"):
    val start = Instant.parse("2026-04-24T10:00:00Z")
    val cfg = Config(velocityThreshold = 10)

    val events = (1 to 10).toList.map { i =>
      enriched(i, start.plusSeconds(i.toLong * 10L), 10)
    }

    val (finalState, finalAssessment) =
      events.foldLeft((RiskState.empty, Option.empty[RiskAssessment])) { case ((st, _), ev) =>
        val (st2, a) = st.next(ev, cfg)
        (st2, Some(a))
      }

    assert(finalState.byCustomer.nonEmpty)
    assert(finalAssessment.exists(_.alerts.exists(_.alertType == AlertType.VelocitySpike)))

  test("night burst near limit triggers after multiple night tx"):
    val base = Instant.parse("2026-04-24T01:00:00Z")
    val cfg = Config(nightBurstThreshold = 3, nightWindow = Duration.ofHours(5))

    val ev1 = enriched(1, base, 900)
    val (s1, a1) = RiskState.empty.next(ev1, cfg)
    assert(!a1.alerts.exists(_.alertType == AlertType.NightBurstNearLimit))

    val ev2 = enriched(2, base.plusSeconds(60 * 30), 900)
    val (s2, a2) = s1.next(ev2, cfg)
    assert(!a2.alerts.exists(_.alertType == AlertType.NightBurstNearLimit))

    val ev3 = enriched(3, base.plusSeconds(60 * 60), 900)
    val (_, a3) = s2.next(ev3, cfg)
    assert(a3.alerts.exists(_.alertType == AlertType.NightBurstNearLimit))

  test("z-score outlier triggers after enough history"):
    val start = Instant.parse("2026-04-24T10:00:00Z")
    val cfg = Config(zScoreMinSamples = 20, iqrMinSamples = 10_000)

    val history = (1 to 20).toList.map { i =>
      enriched(i, start.plusSeconds(i.toLong), BigDecimal(100 + i))
    }

    val (stateAfterHistory, _) = history.foldLeft((RiskState.empty, RiskAssessment(0, Nil))) {
      case ((st, _), ev) => st.next(ev, cfg)
    }

    val outlier = enriched(100, start.plusSeconds(1000), BigDecimal(1000))
    val (_, assessment) = stateAfterHistory.next(outlier, cfg)

    assert(assessment.alerts.exists(_.alertType == AlertType.ZScoreAmountOutlier))

  test("IQR outlier triggers after enough history"):
    val start = Instant.parse("2026-04-24T10:00:00Z")
    val cfg = Config(iqrMinSamples = 20, zScoreMinSamples = 10_000)

    val history = (1 to 20).toList.map { i =>
      enriched(i, start.plusSeconds(i.toLong), BigDecimal(100 + i))
    }

    val (stateAfterHistory, _) = history.foldLeft((RiskState.empty, RiskAssessment(0, Nil))) {
      case ((st, _), ev) => st.next(ev, cfg)
    }

    val outlier = enriched(100, start.plusSeconds(1000), BigDecimal(1000))
    val (_, assessment) = stateAfterHistory.next(outlier, cfg)

    assert(assessment.alerts.exists(_.alertType == AlertType.IqrAmountOutlier))
