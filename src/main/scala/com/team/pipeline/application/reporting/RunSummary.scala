package com.team.pipeline.application.reporting

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
