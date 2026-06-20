package com.team.pipeline.dashboard

import com.raquo.laminar.api.L.*
import com.team.pipeline.dashboard.contract.DashboardDataset

/** Mutable, browser-side filter inputs. Raw strings are converted to a [[FilterState]] in `Main`. */
final case class FilterVars(
    from: Var[String],
    to: Var[String],
    customerId: Var[String],
    finalDecisions: Var[Set[String]],
    alertTypes: Var[Set[String]],
    countries: Var[Set[String]]
)

object FilterVars:
  def create(): FilterVars =
    FilterVars(
      from = Var(""),
      to = Var(""),
      customerId = Var(""),
      finalDecisions = Var(Set.empty),
      alertTypes = Var(Set.empty),
      countries = Var(Set.empty)
    )

object Filters:

  /** Build the filter panel; option lists (decisions/countries/alert types) come from the data. */
  def panel(dataset: DashboardDataset, vars: FilterVars): HtmlElement =
    val decisions = dataset.events.map(_.finalDecision).distinct.sorted
    val countries = dataset.events.map(_.transactionCountry).distinct.sorted
    val alertTypes = dataset.alerts.map(_.alertType).distinct.sorted

    div(
      cls := "panel filters",
      div(
        cls := "filters-row",
        textField("From", "datetime-local", "", vars.from),
        textField("To", "datetime-local", "", vars.to),
        textField("Customer ID", "number", "any", vars.customerId)
      ),
      chipGroup("Final decision", decisions, vars.finalDecisions, Some(Charts.decisionColor)),
      chipGroup("Alert type", alertTypes, vars.alertTypes, None),
      chipGroup("Country", countries, vars.countries, None),
      button(
        cls := "btn ghost",
        "Clear filters",
        onClick --> (_ => clearAll(vars))
      )
    )

  private def textField(label: String, inputType: String, placeholderText: String, v: Var[String]) =
    div(
      cls := "field",
      span(cls := "field-label", label),
      input(
        typ := inputType,
        placeholder := placeholderText,
        controlled(value <-- v.signal, onInput.mapToValue --> v)
      )
    )

  private def chipGroup(
      label: String,
      options: List[String],
      v: Var[Set[String]],
      color: Option[String => String]
  ): HtmlElement =
    div(
      cls := "filter-group",
      span(cls := "filter-label", label),
      div(
        cls := "chips",
        if options.isEmpty then span(cls := "notice", "—")
        else options.map(option => chip(option, v, color))
      )
    )

  private def chip(option: String, v: Var[Set[String]], color: Option[String => String]): HtmlElement =
    button(
      cls := "chip",
      cls.toggle("active") <-- v.signal.map(_.contains(option)),
      color.map { colorOf =>
        styleAttr <-- v.signal.map { selected =>
          if selected.contains(option) then s"background:${colorOf(option)};border-color:${colorOf(option)}"
          else ""
        }
      },
      option,
      onClick --> { _ =>
        v.update(selected => if selected.contains(option) then selected - option else selected + option)
      }
    )

  private def clearAll(vars: FilterVars): Unit =
    vars.from.set("")
    vars.to.set("")
    vars.customerId.set("")
    vars.finalDecisions.set(Set.empty)
    vars.alertTypes.set(Set.empty)
    vars.countries.set(Set.empty)
