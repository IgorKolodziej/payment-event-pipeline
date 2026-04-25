package com.team.pipeline.application.reporting

final case class DashboardSnapshot(
    totalProcessed: Int,
    totalRejected: Int,
    totalAlerts: Int,
    alertCounts: Map[String, Int],
    decisionCounts: Map[String, Int],
    topCountries: Map[String, Int]
)
