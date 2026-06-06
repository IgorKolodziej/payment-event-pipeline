package com.team.pipeline.dashboard.contract

import io.circe.Codec
import io.circe.generic.semiauto._

/** Single transaction row used by the dashboard for filters, KPIs and charts.
  *
  * This is the privacy-safe projection of `ProcessedEvent`: it intentionally does NOT carry
  * `hashedCustomerEmail` (or any raw email). `amount` is a string and `timestamp` is an ISO-8601
  * string so the JSON contract has no decimal/date surprises across the BE/FE boundary.
  */
final case class DashboardEvent(
    eventId: Int,
    timestamp: String,
    customerId: Int,
    amount: String,
    currency: String,
    status: String,
    paymentMethod: String,
    transactionCountry: String,
    merchantCategory: String,
    channel: String,
    deviceId: String,
    riskScore: Int,
    riskDecision: String,
    finalDecision: String
)

object DashboardEvent:
  given Codec[DashboardEvent] = deriveCodec[DashboardEvent]

/** Risk alert linked to an event, used for the alert breakdown chart and row details. */
final case class DashboardAlert(
    eventId: Int,
    customerId: Int,
    alertType: String,
    riskScore: Int
)

object DashboardAlert:
  given Codec[DashboardAlert] = deriveCodec[DashboardAlert]

/** Eligibility violation linked to an event, used for row details. */
final case class DashboardViolation(
    eventId: Int,
    customerId: Int,
    violationType: String
)

object DashboardViolation:
  given Codec[DashboardViolation] = deriveCodec[DashboardViolation]

/** Top-level dashboard dataset contract written to `out/dashboard_dataset.json`.
  *
  * This is the single, stable shape shared by the backend exporter and the Scala.js frontend.
  * Minimum guaranteed fields: `generatedAt`, `events`, `alerts`, `violations`.
  */
final case class DashboardDataset(
    generatedAt: String,
    events: List[DashboardEvent],
    alerts: List[DashboardAlert],
    violations: List[DashboardViolation]
)

object DashboardDataset:
  given Codec[DashboardDataset] = deriveCodec[DashboardDataset]

  val empty: DashboardDataset =
    DashboardDataset(
      generatedAt = "",
      events = List.empty,
      alerts = List.empty,
      violations = List.empty
    )
