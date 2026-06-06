package com.team.pipeline.dashboard

import com.raquo.laminar.api.L.*
import com.team.pipeline.dashboard.contract.DashboardDataset
import org.scalajs.dom

import scala.scalajs.js.timers.*

/** Entry point of the Scala.js + Laminar payment-events dashboard. */
object Main:

  enum LoadState:
    case Loading
    case Loaded(dataset: DashboardDataset)
    case Failed(message: String)

  private val state: Var[LoadState] = Var(LoadState.Loading)
  private val filterVars: FilterVars = FilterVars.create()
  private val selectedEvent: Var[Option[Int]] = Var(None)
  private val autoRefresh: Var[Boolean] = Var(false)

  private var timerHandle: Option[SetIntervalHandle] = None

  private val filterStateSignal: Signal[FilterState] =
    filterVars.from.signal
      .combineWith(
        filterVars.to.signal,
        filterVars.customerId.signal,
        filterVars.finalDecisions.signal,
        filterVars.alertTypes.signal,
        filterVars.countries.signal
      )
      .map { (from, to, customerId, decisions, alertTypes, countries) =>
        FilterState(
          from = opt(from),
          to = opt(to),
          customerId = customerId.trim.toIntOption,
          finalDecisions = decisions,
          alertTypes = alertTypes,
          countries = countries
        )
      }

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), appElement())
    load(initial = true)

  private def opt(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def load(initial: Boolean): Unit =
    if initial then state.set(LoadState.Loading)
    DataLoader.load {
      case Right(dataset) => state.set(LoadState.Loaded(dataset))
      case Left(message)  => state.set(LoadState.Failed(message))
    }

  private def startTimer(): Unit =
    stopTimer()
    timerHandle = Some(setInterval(2000.0)(load(initial = false)))

  private def stopTimer(): Unit =
    timerHandle.foreach(clearInterval)
    timerHandle = None

  private def appElement(): HtmlElement =
    div(
      cls := "app",
      autoRefresh.signal --> Observer[Boolean] { enabled =>
        if enabled then startTimer() else stopTimer()
      },
      headerBar(),
      child <-- state.signal.map(renderState)
    )

  private def headerBar(): HtmlElement =
    headerTag(
      cls := "app-header",
      div(
        cls := "app-title",
        h1("Payment Event Dashboard"),
        p(cls := "app-subtitle", "Risk & eligibility overview")
      ),
      div(
        cls := "controls",
        span(
          cls := "generated",
          child.text <-- state.signal.map {
            case LoadState.Loaded(dataset) => s"generated: ${dataset.generatedAt}"
            case _                         => ""
          }
        ),
        label(
          cls := "toggle",
          input(
            typ := "checkbox",
            checked <-- autoRefresh.signal,
            onInput.mapToChecked --> autoRefresh
          ),
          span("Auto-refresh (2s)")
        ),
        button(cls := "btn", "Refresh now", onClick --> (_ => load(initial = false)))
      )
    )

  private def renderState(loadState: LoadState): HtmlElement =
    loadState match
      case LoadState.Loading =>
        div(cls := "panel notice", "Loading dataset…")
      case LoadState.Failed(message) =>
        div(cls := "panel notice error", message)
      case LoadState.Loaded(dataset) =>
        loadedLayout(dataset)

  private def loadedLayout(dataset: DashboardDataset): HtmlElement =
    div(
      Filters.panel(dataset, filterVars),
      child <-- filterStateSignal.map { filterState =>
        dashboardBody(dataset, Analytics.view(dataset, filterState))
      }
    )

  private def dashboardBody(dataset: DashboardDataset, view: DashboardView): HtmlElement =
    div(
      Components.kpiTiles(view),
      div(
        cls := "charts-grid",
        Charts.timeSeries("Transactions per day", Analytics.countsByDay(view.events)),
        Charts.horizontalBars(
          "Final decision",
          Analytics.sortedDescByCount(Analytics.countBy(view.events)(_.finalDecision)),
          Charts.decisionColor
        ),
        Charts.horizontalBars(
          "Top alert types",
          Analytics.topN(Analytics.countBy(view.alerts)(_.alertType), 8)
        ),
        Charts.horizontalBars(
          "Top countries",
          Analytics.topN(Analytics.countBy(view.events)(_.transactionCountry), 8)
        )
      ),
      div(
        cls := "tx-grid",
        Components.transactionsTable(view.events, selectedEvent),
        Components.detailsPanel(dataset, selectedEvent)
      )
    )
