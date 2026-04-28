package com.team.pipeline.application.reporting

import cats.data.NonEmptyChain
import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.AlertType
import com.team.pipeline.domain.DataError
import com.team.pipeline.domain.FinalDecision

final case class RunSummary(
    totalRead: Int,
    totalProcessed: Int,
    totalRejected: Int,
    totalAlerts: Int,
    errorCounts: Map[String, Int],
    alertCounts: Map[String, Int],
    decisionCounts: Map[String, Int],
    countryCounts: Map[String, Int]
)

object RunSummary:
  val empty: RunSummary =
    RunSummary(
      totalRead = 0,
      totalProcessed = 0,
      totalRejected = 0,
      totalAlerts = 0,
      errorCounts = Map.empty,
      alertCounts = Map.empty,
      decisionCounts = Map.empty,
      countryCounts = Map.empty
    )

  private[reporting] def increment(
      counts: Map[String, Int],
      key: String,
      by: Int = 1
  ): Map[String, Int] =
    if by == 0 then counts
    else
      counts.updatedWith(key) {
        case Some(current) => Some(current + by)
        case None          => Some(by)
      }

  private[reporting] def errorKey(error: DataError): String =
    if error == null then "null"
    else error.getClass.getSimpleName.stripSuffix("$")

  private[reporting] def normalizeCountry(country: String): String =
    country.trim.toUpperCase

extension (summary: RunSummary)
  def onLineRead: RunSummary =
    summary.copy(totalRead = summary.totalRead + 1)

  def onRejected(reason: DataError): RunSummary =
    summary.onRejected(NonEmptyChain.one(reason))

  def onRejected(reasons: NonEmptyChain[DataError]): RunSummary =
    val errorCounts =
      reasons.toNonEmptyList.toList.foldLeft(summary.errorCounts) { (counts, reason) =>
        RunSummary.increment(counts, RunSummary.errorKey(reason))
      }

    summary.copy(
      totalRejected = summary.totalRejected + 1,
      errorCounts = errorCounts
    )

  def onProcessed(transactionCountry: String): RunSummary =
    val country = RunSummary.normalizeCountry(transactionCountry)
    summary.copy(
      totalProcessed = summary.totalProcessed + 1,
      countryCounts = RunSummary.increment(summary.countryCounts, country)
    )

  def onDecision(decision: FinalDecision): RunSummary =
    val key = decision.code
    summary.copy(
      decisionCounts = RunSummary.increment(summary.decisionCounts, key)
    )

  def onAlert(alertType: AlertType): RunSummary =
    val key = alertType.code
    summary.copy(
      totalAlerts = summary.totalAlerts + 1,
      alertCounts = RunSummary.increment(summary.alertCounts, key)
    )

  def onAlerts(alerts: Iterable[Alert]): RunSummary =
    alerts.foldLeft(summary) { (s, alert) =>
      s.onAlert(alert.alertType)
    }

  def toDashboardSnapshot(topCountries: Int): DashboardSnapshot =
    DashboardSnapshot.from(summary, topCountries = topCountries)
