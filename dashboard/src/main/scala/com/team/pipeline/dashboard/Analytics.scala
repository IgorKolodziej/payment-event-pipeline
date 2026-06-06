package com.team.pipeline.dashboard

import com.team.pipeline.dashboard.contract.DashboardAlert
import com.team.pipeline.dashboard.contract.DashboardDataset
import com.team.pipeline.dashboard.contract.DashboardEvent

import scala.scalajs.js

/** Selected filter values. Empty sets / `None` mean "no constraint". */
final case class FilterState(
    from: Option[String],
    to: Option[String],
    customerId: Option[Int],
    finalDecisions: Set[String],
    alertTypes: Set[String],
    countries: Set[String]
)

object FilterState:
  val empty: FilterState =
    FilterState(
      from = None,
      to = None,
      customerId = None,
      finalDecisions = Set.empty,
      alertTypes = Set.empty,
      countries = Set.empty
    )

/** The dataset narrowed down to the current filter selection, with derived collections that the
  * KPI tiles, charts and table all read from.
  */
final case class DashboardView(
    dataset: DashboardDataset,
    events: List[DashboardEvent],
    alerts: List[DashboardAlert]
)

/** Pure, browser-side analytics: filtering and the aggregations used by the charts and KPIs. */
object Analytics:

  def parseAmount(raw: String): Double =
    raw.toDoubleOption.getOrElse(0.0)

  /** Epoch millis for an ISO-8601 / `datetime-local` string, or NaN if unparseable. */
  private def epochMillis(value: String): Double =
    new js.Date(value).getTime()

  /** Calendar day (`yyyy-MM-dd`) of an ISO-8601 timestamp. */
  def dayOf(timestamp: String): String =
    timestamp.take(10)

  private def withinRange(timestamp: String, from: Option[String], to: Option[String]): Boolean =
    val eventMillis = epochMillis(timestamp)
    if eventMillis.isNaN then true
    else
      val afterFrom = from.filter(_.nonEmpty).forall { f =>
        val m = epochMillis(f)
        m.isNaN || eventMillis >= m
      }
      val beforeTo = to.filter(_.nonEmpty).forall { t =>
        val m = epochMillis(t)
        m.isNaN || eventMillis <= m
      }
      afterFrom && beforeTo

  def view(dataset: DashboardDataset, filter: FilterState): DashboardView =
    val alertTypesByEvent: Map[Int, Set[String]] =
      dataset.alerts.groupBy(_.eventId).view.mapValues(_.map(_.alertType).toSet).toMap

    val events = dataset.events.filter { event =>
      withinRange(event.timestamp, filter.from, filter.to) &&
      filter.customerId.forall(_ == event.customerId) &&
      (filter.finalDecisions.isEmpty || filter.finalDecisions.contains(event.finalDecision)) &&
      (filter.countries.isEmpty || filter.countries.contains(event.transactionCountry)) &&
      (filter.alertTypes.isEmpty ||
        alertTypesByEvent.getOrElse(event.eventId, Set.empty).exists(filter.alertTypes.contains))
    }

    val eventIds = events.map(_.eventId).toSet
    val alerts = dataset.alerts.filter(alert => eventIds.contains(alert.eventId))
    DashboardView(dataset, events, alerts)

  def countBy[A](items: List[A])(key: A => String): List[(String, Int)] =
    items.groupBy(key).view.mapValues(_.size).toList

  def sortedDescByCount(counts: List[(String, Int)]): List[(String, Int)] =
    counts.sortBy { case (label, count) => (-count, label) }

  def topN(counts: List[(String, Int)], n: Int): List[(String, Int)] =
    sortedDescByCount(counts).take(n)

  def countsByDay(events: List[DashboardEvent]): List[(String, Int)] =
    countBy(events)(e => dayOf(e.timestamp)).sortBy(_._1)

  def mean(values: List[Double]): Double =
    if values.isEmpty then 0.0 else values.sum / values.size

  def median(values: List[Double]): Double =
    if values.isEmpty then 0.0
    else
      val sorted = values.sorted
      val mid = sorted.size / 2
      if sorted.size % 2 == 1 then sorted(mid)
      else (sorted(mid - 1) + sorted(mid)) / 2.0

  /** Compact money formatting, e.g. 1234.5 -> "1,234.50". */
  def formatMoney(value: Double): String =
    val rounded = Math.round(value * 100.0) / 100.0
    val str = f"$rounded%,.2f"
    str
