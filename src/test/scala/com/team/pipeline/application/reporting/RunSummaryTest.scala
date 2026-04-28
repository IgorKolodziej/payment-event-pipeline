package com.team.pipeline.application.reporting

import cats.data.NonEmptyChain
import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.AlertType
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.InvalidAmount
import com.team.pipeline.domain.InvalidJson
import munit.FunSuite

class RunSummaryTest extends FunSuite:
  test("empty has zero totals and empty maps") {
    val s = RunSummary.empty

    assertEquals(s.totalRead, 0)
    assertEquals(s.totalProcessed, 0)
    assertEquals(s.totalRejected, 0)
    assertEquals(s.totalAlerts, 0)
    assertEquals(s.errorCounts, Map.empty)
    assertEquals(s.alertCounts, Map.empty)
    assertEquals(s.decisionCounts, Map.empty)
    assertEquals(s.countryCounts, Map.empty)
  }

  test("onLineRead increments totalRead") {
    val s = RunSummary.empty.onLineRead.onLineRead
    assertEquals(s.totalRead, 2)
    assertEquals(s.totalRejected, 0)
  }

  test("onRejected increments totalRejected and errorCounts by error type") {
    val s = RunSummary.empty.onRejected(InvalidJson("boom"))

    assertEquals(s.totalRejected, 1)
    assertEquals(s.errorCounts, Map("InvalidJson" -> 1))
  }

  test("onRejected counts every reason while incrementing totalRejected once") {
    val s = RunSummary.empty.onRejected(
      NonEmptyChain.of(
        InvalidJson("boom"),
        InvalidAmount(BigDecimal("-1.00"))
      )
    )

    assertEquals(s.totalRejected, 1)
    assertEquals(
      s.errorCounts,
      Map(
        "InvalidJson" -> 1,
        "InvalidAmount" -> 1
      )
    )
  }

  test("onProcessed increments totalProcessed and normalizes country") {
    val s = RunSummary.empty.onProcessed(" pl ").onProcessed("PL")

    assertEquals(s.totalProcessed, 2)
    assertEquals(s.countryCounts, Map("PL" -> 2))
  }

  test("onDecision increments decisionCounts") {
    val s = RunSummary.empty.onDecision(FinalDecision.Accepted).onDecision(FinalDecision.Accepted)

    assertEquals(s.decisionCounts, Map("Accepted" -> 2))
  }

  test("onAlert increments totalAlerts and alertCounts") {
    val s = RunSummary.empty.onAlert(AlertType.VelocitySpike).onAlert(AlertType.VelocitySpike)

    assertEquals(s.totalAlerts, 2)
    assertEquals(s.alertCounts, Map("VelocitySpike" -> 2))
  }

  test("onAlerts folds over all alerts") {
    val alerts = List(
      Alert(
        alertType = AlertType.CountryMismatch,
        eventId = 1,
        customerId = 10,
        message = "mismatch",
        riskScore = 20
      ),
      Alert(
        alertType = AlertType.CountryMismatch,
        eventId = 2,
        customerId = 11,
        message = "mismatch",
        riskScore = 15
      ),
      Alert(
        alertType = AlertType.LateNightTransaction,
        eventId = 3,
        customerId = 12,
        message = "late",
        riskScore = 10
      )
    )

    val s = RunSummary.empty.onAlerts(alerts)

    assertEquals(s.totalAlerts, 3)
    assertEquals(
      s.alertCounts,
      Map(
        "CountryMismatch" -> 2,
        "LateNightTransaction" -> 1
      )
    )
  }

  test("toDashboardSnapshot keeps totals and selects deterministic top countries") {
    val summary = RunSummary.empty
      .onProcessed("PL")
      .onProcessed("US")
      .onProcessed("DE")
      .onProcessed("DE")
      .onProcessed("US")
      .onRejected(InvalidJson("x"))
      .onAlert(AlertType.VelocitySpike)
      .onDecision(FinalDecision.Accepted)

    val snap = summary.toDashboardSnapshot(topCountries = 2)

    assertEquals(snap.totalProcessed, 5)
    assertEquals(snap.totalRejected, 1)
    assertEquals(snap.totalAlerts, 1)
    assertEquals(snap.alertCounts, Map("VelocitySpike" -> 1))
    assertEquals(snap.decisionCounts, Map("Accepted" -> 1))

    // Expect DE (2) then US (2): count desc, then country asc tie-break.
    assertEquals(snap.topCountries.toList, List("DE" -> 2, "US" -> 2))
  }
