package com.team.pipeline.dashboard

import com.raquo.laminar.api.L.*
import com.team.pipeline.dashboard.contract.DashboardDataset
import org.scalajs.dom

/** Entry point of the Scala.js + Laminar payment-events dashboard. */
object Main:

  enum LoadState:
    case Loading
    case Loaded(dataset: DashboardDataset)
    case Failed(message: String)

  private val state: Var[LoadState] = Var(LoadState.Loading)

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), appElement())
    load()

  private def load(): Unit =
    state.set(LoadState.Loading)
    DataLoader.load {
      case Right(dataset) => state.set(LoadState.Loaded(dataset))
      case Left(message)  => state.set(LoadState.Failed(message))
    }

  private def appElement(): HtmlElement =
    div(
      cls := "app",
      headerTag(
        cls := "app-header",
        div(
          cls := "app-title",
          h1("Payment Event Dashboard"),
          p(cls := "app-subtitle", "Risk & eligibility overview")
        ),
        div(
          cls := "controls",
          button(cls := "btn", "Refresh now", onClick --> (_ => load()))
        )
      ),
      child <-- state.signal.map(renderState)
    )

  private def renderState(loadState: LoadState): HtmlElement =
    loadState match
      case LoadState.Loading =>
        div(cls := "panel notice", "Loading dataset…")
      case LoadState.Failed(message) =>
        div(cls := "panel notice error", message)
      case LoadState.Loaded(dataset) =>
        renderSummary(dataset)

  private def renderSummary(dataset: DashboardDataset): HtmlElement =
    div(
      cls := "panel",
      p(s"Generated at: ${dataset.generatedAt}"),
      ul(
        li(s"Events: ${dataset.events.size}"),
        li(s"Alerts: ${dataset.alerts.size}"),
        li(s"Violations: ${dataset.violations.size}")
      )
    )
