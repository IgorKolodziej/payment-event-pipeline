package com.team.pipeline.dashboard

import com.raquo.laminar.api.L.*
import com.team.pipeline.dashboard.contract.DashboardDataset
import com.team.pipeline.dashboard.contract.DashboardEvent

/** KPI tiles, the transactions table and the row-details panel. */
object Components:

  private val decisionOrder = List("Accepted", "Declined", "Review", "BlockedByRisk")

  def kpiTiles(view: DashboardView): HtmlElement =
    val amounts = view.events.map(event => Analytics.parseAmount(event.amount))
    val decisionCounts = Analytics.countBy(view.events)(_.finalDecision).toMap

    div(
      cls := "kpi-grid",
      tile("Total processed", view.events.size.toString, None),
      tile("Total alerts", view.alerts.size.toString, None),
      tile("Avg amount", Analytics.formatMoney(Analytics.mean(amounts)), None),
      tile("Median amount", Analytics.formatMoney(Analytics.median(amounts)), None),
      decisionOrder.map { decision =>
        tile(decision, decisionCounts.getOrElse(decision, 0).toString, Some(decision))
      }
    )

  private def tile(label: String, value: String, decision: Option[String]): HtmlElement =
    div(
      cls := "kpi-tile",
      decision.map(d => borderStyle(Charts.decisionColor(d))),
      div(cls := "kpi-value", value),
      div(cls := "kpi-label", label)
    )

  private def borderStyle(color: String): Mod[HtmlElement] =
    styleAttr := s"border-top: 3px solid $color"

  def transactionsTable(events: List[DashboardEvent], selected: Var[Option[Int]]): HtmlElement =
    val rows = events.sortBy(_.timestamp)(Ordering[String].reverse).take(100)
    div(
      cls := "panel",
      h3(cls := "chart-title", s"Transactions (${rows.size} of ${events.size})"),
      div(
        cls := "table-wrap",
        table(
          cls := "tx-table",
          thead(
            tr(
              th("timestamp"),
              th("eventId"),
              th("customerId"),
              th("amount"),
              th("country"),
              th("method"),
              th("finalDecision"),
              th("riskScore")
            )
          ),
          tbody(
            rows.map(eventRow(_, selected))
          )
        )
      )
    )

  private def eventRow(event: DashboardEvent, selected: Var[Option[Int]]): HtmlElement =
    tr(
      cls := "tx-row",
      cls.toggle("selected") <-- selected.signal.map(_.contains(event.eventId)),
      onClick --> (_ => selected.set(Some(event.eventId))),
      td(event.timestamp.replace("T", " ").replace("Z", "")),
      td(event.eventId.toString),
      td(event.customerId.toString),
      td(cls := "num", s"${event.amount} ${event.currency}"),
      td(event.transactionCountry),
      td(event.paymentMethod),
      td(decisionBadge(event.finalDecision)),
      td(cls := "num", event.riskScore.toString)
    )

  private def decisionBadge(decision: String): HtmlElement =
    span(
      cls := "badge",
      styleAttr := s"background:${Charts.decisionColor(decision)}",
      decision
    )

  def detailsPanel(dataset: DashboardDataset, selected: Var[Option[Int]]): HtmlElement =
    div(
      cls := "panel",
      h3(cls := "chart-title", "Transaction details"),
      child <-- selected.signal.map {
        case None =>
          div(cls := "notice", "Select a transaction row to see its alerts and violations.")
        case Some(eventId) =>
          renderDetails(dataset, eventId)
      }
    )

  private def renderDetails(dataset: DashboardDataset, eventId: Int): HtmlElement =
    val alerts = dataset.alerts.filter(_.eventId == eventId)
    val violations = dataset.violations.filter(_.eventId == eventId)
    div(
      cls := "details",
      p(cls := "details-head", s"Event #$eventId"),
      div(
        cls := "details-cols",
        div(
          cls := "details-col",
          h4(s"Alerts (${alerts.size})"),
          if alerts.isEmpty then p(cls := "notice", "No alerts.")
          else
            ul(
              alerts.map { alert =>
                li(s"${alert.alertType} (risk ${alert.riskScore}, customer ${alert.customerId})")
              }
            )
        ),
        div(
          cls := "details-col",
          h4(s"Violations (${violations.size})"),
          if violations.isEmpty then p(cls := "notice", "No violations.")
          else
            ul(
              violations.map { violation =>
                li(s"${violation.violationType} (customer ${violation.customerId})")
              }
            )
        )
      )
    )
