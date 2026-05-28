package com.team.pipeline.reporting

import munit.FunSuite
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import io.circe.parser.parse
import com.team.pipeline.application.reporting.RunSummary
import com.team.pipeline.application.reporting.DashboardSnapshot
import com.team.pipeline.application.reporting._
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.AlertType
import java.util.Comparator

class JsonWritersTest extends FunSuite:
  test("writes RunSummary as JSON file") {
    val tmpDir = Files.createTempDirectory("report-test")
    try
      val summary = RunSummary.empty
        .onLineRead
        .onProcessed("PL")
        .onDecision(FinalDecision.Accepted)
        .onAlert(AlertType.CountryMismatch)

      val reportPath = tmpDir.resolve("report.json")
      com.team.pipeline.reporting.JsonWriters.writeJsonToFile[RunSummary](summary, reportPath)

      val content = new String(Files.readAllBytes(reportPath), StandardCharsets.UTF_8)
      val json = parse(content).getOrElse(fail("invalid json"))
      val cursor = json.hcursor

      assertEquals(cursor.get[Int]("totalProcessed").toOption.get, summary.totalProcessed)
      assertEquals(cursor.get[Int]("totalAlerts").toOption.get, summary.totalAlerts)
      assert(cursor.downField("decisionCounts").succeeded)
    finally
      // cleanup
      Files.walk(tmpDir)
        .sorted(Comparator.reverseOrder())
        .forEach(path => Files.deleteIfExists(path))
  }

  test("writes DashboardSnapshot as JSON file") {
    val tmpDir = Files.createTempDirectory("dashboard-test")
    try
      val summary = RunSummary.empty
        .onProcessed("PL")
        .onProcessed("US")
        .onProcessed("DE")
        .onRejected(com.team.pipeline.domain.InvalidJson("x"))
        .onAlert(AlertType.LateNightTransaction)
        .onDecision(FinalDecision.Review)

      val snapshot = summary.toDashboardSnapshot(topCountries = 2)
      val dashPath = tmpDir.resolve("dashboard.json")
      com.team.pipeline.reporting.JsonWriters.writeJsonToFile[DashboardSnapshot](snapshot, dashPath)

      val content = new String(Files.readAllBytes(dashPath), StandardCharsets.UTF_8)
      val json = parse(content).getOrElse(fail("invalid json"))
      val cursor = json.hcursor

      assertEquals(cursor.get[Int]("totalProcessed").toOption.get, snapshot.totalProcessed)
      assertEquals(cursor.get[Int]("totalRejected").toOption.get, snapshot.totalRejected)
      assert(cursor.downField("topCountries").succeeded)
    finally
      Files.walk(tmpDir)
        .sorted(Comparator.reverseOrder())
        .forEach(path => Files.deleteIfExists(path))
  }
