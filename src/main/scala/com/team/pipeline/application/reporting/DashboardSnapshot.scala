package com.team.pipeline.application.reporting

import scala.collection.immutable.ListMap

final case class DashboardSnapshot(
    totalProcessed: Int,
    totalRejected: Int,
    totalAlerts: Int,
    alertCounts: Map[String, Int],
    decisionCounts: Map[String, Int],
    topCountries: Map[String, Int]
)

object DashboardSnapshot:
  def from(
      summary: RunSummary,
      topCountries: Int
  ): DashboardSnapshot =
    DashboardSnapshot(
      totalProcessed = summary.totalProcessed,
      totalRejected = summary.totalRejected,
      totalAlerts = summary.totalAlerts,
      alertCounts = summary.alertCounts,
      decisionCounts = summary.decisionCounts,
      topCountries = topCountriesFrom(summary.countryCounts, topCountries)
    )

  private[reporting] def topCountriesFrom(
      countryCounts: Map[String, Int],
      topN: Int
  ): Map[String, Int] =
    if topN <= 0 then Map.empty
    else
      val entries =
        countryCounts.toList
          .sortBy { case (country, count) => (-count, country) }
          .take(topN)

      ListMap.from(entries)
