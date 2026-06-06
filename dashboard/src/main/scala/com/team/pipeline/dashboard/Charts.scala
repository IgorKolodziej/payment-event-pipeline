package com.team.pipeline.dashboard

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.svg
import com.raquo.laminar.nodes.ReactiveElement

/** Small, dependency-free SVG charts (no Chart.js / no CDN). */
object Charts:

  private val accent = "#38bdf8"

  /** Stable colour per finalDecision so the decision chart reads at a glance. */
  def decisionColor(label: String): String =
    label match
      case "Accepted"      => "#22c55e"
      case "Declined"      => "#f97316"
      case "Review"        => "#eab308"
      case "BlockedByRisk" => "#ef4444"
      case _               => accent

  def constantColor(color: String): String => String = _ => color

  private def truncate(label: String, max: Int): String =
    if label.length <= max then label else label.take(max - 1) + "\u2026"

  /** Vertical bar chart, used for the per-day time series. */
  def timeSeries(title: String, data: List[(String, Int)]): HtmlElement =
    chartPanel(title) {
      if data.isEmpty then emptyChart()
      else
        val height = 240
        val padTop = 24
        val padBottom = 48
        val padLeft = 8
        val slot = 60
        val barWidth = 34
        val plotHeight = height - padTop - padBottom
        val maxValue = data.map(_._2).max.max(1)
        val width = (padLeft * 2 + data.size * slot).max(slot)

        svg.svg(
          svg.viewBox := s"0 0 $width $height",
          svg.width := "100%",
          svg.height := s"${height}px",
          svg.g(
            data.zipWithIndex.map { case ((label, value), index) =>
              val barHeight = Math.round(value.toDouble / maxValue * plotHeight).toInt.max(1)
              val x = padLeft + index * slot + (slot - barWidth) / 2
              val y = padTop + (plotHeight - barHeight)
              svg.g(
                svg.rect(
                  svg.x := x.toString,
                  svg.y := y.toString,
                  svg.width := barWidth.toString,
                  svg.height := barHeight.toString,
                  svg.rx := "4",
                  svg.fill := accent
                ),
                svg.text(
                  svg.x := (x + barWidth / 2).toString,
                  svg.y := (y - 6).toString,
                  svg.textAnchor := "middle",
                  svg.fontSize := "11",
                  svg.fill := "#e2e8f0",
                  value.toString
                ),
                svg.text(
                  svg.x := (x + barWidth / 2).toString,
                  svg.y := (padTop + plotHeight + 18).toString,
                  svg.textAnchor := "middle",
                  svg.fontSize := "10",
                  svg.fill := "#94a3b8",
                  if label.length >= 10 then label.drop(5) else label
                )
              )
            }
          )
        )
    }

  /** Horizontal bar chart, used for decision / alert / country breakdowns. */
  def horizontalBars(
      title: String,
      data: List[(String, Int)],
      color: String => String = constantColor(accent)
  ): HtmlElement =
    chartPanel(title) {
      if data.isEmpty then emptyChart()
      else
        val rowHeight = 30
        val padTop = 10
        val labelWidth = 130
        val barStart = labelWidth + 8
        val barMax = 210
        val width = barStart + barMax + 52
        val height = padTop * 2 + data.size * rowHeight
        val maxValue = data.map(_._2).max.max(1)

        svg.svg(
          svg.viewBox := s"0 0 $width $height",
          svg.width := "100%",
          svg.height := s"${height}px",
          svg.g(
            data.zipWithIndex.map { case ((label, value), index) =>
              val y = padTop + index * rowHeight
              val barWidth = Math.round(value.toDouble / maxValue * barMax).toInt.max(1)
              svg.g(
                svg.text(
                  svg.x := "8",
                  svg.y := (y + rowHeight / 2 + 4).toString,
                  svg.fontSize := "12",
                  svg.fill := "#cbd5e1",
                  truncate(label, 18)
                ),
                svg.rect(
                  svg.x := barStart.toString,
                  svg.y := (y + 5).toString,
                  svg.width := barWidth.toString,
                  svg.height := (rowHeight - 12).toString,
                  svg.rx := "4",
                  svg.fill := color(label)
                ),
                svg.text(
                  svg.x := (barStart + barWidth + 6).toString,
                  svg.y := (y + rowHeight / 2 + 4).toString,
                  svg.fontSize := "11",
                  svg.fill := "#e2e8f0",
                  value.toString
                )
              )
            }
          )
        )
    }

  private def emptyChart(): HtmlElement =
    div(cls := "chart-empty", "No data for the current filters.")

  private def chartPanel(title: String)(body: ReactiveElement.Base): HtmlElement =
    div(
      cls := "panel chart",
      h3(cls := "chart-title", title),
      body
    )
