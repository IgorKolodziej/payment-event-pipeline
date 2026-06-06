package com.team.pipeline.dashboard

import com.team.pipeline.dashboard.contract.DashboardDataset
import io.circe.parser.decode
import org.scalajs.dom

/** Loads the dashboard dataset from `out/dashboard_dataset.json` via a plain XHR and decodes it
  * with the shared Circe contract. No backend HTTP API is involved: the file is served statically.
  */
object DataLoader:

  /** Relative to `dashboard/index.html`, the static server exposes the repo root, so the dataset
    * produced by the backend exporter lives one directory up under `out/`.
    */
  val datasetUrl: String = "../out/dashboard_dataset.json"

  def load(onResult: Either[String, DashboardDataset] => Unit): Unit =
    val cacheBustingUrl = s"$datasetUrl?ts=${System.currentTimeMillis()}"
    val xhr = new dom.XMLHttpRequest()
    xhr.open("GET", cacheBustingUrl)
    xhr.onload = { (_: dom.Event) =>
      if xhr.status >= 200 && xhr.status < 300 then
        decode[DashboardDataset](xhr.responseText) match
          case Right(dataset) => onResult(Right(dataset))
          case Left(error)    => onResult(Left(s"Could not parse dataset: ${error.getMessage}"))
      else
        onResult(Left(s"Could not load dataset (HTTP ${xhr.status}). Did you generate out/?"))
    }
    xhr.onerror = { (_: dom.Event) =>
      onResult(Left("Network error while loading dataset."))
    }
    xhr.send()
