package com.team.pipeline.tools

import munit.FunSuite
import com.team.pipeline.dashboard.contract._
import io.circe.syntax._
import io.circe.parser.parse

class ExportDashboardDatasetTest extends FunSuite:
  test("mapper and serialization exclude hashedCustomerEmail") {
    // Build a sample contract DTO
    val event = DashboardEvent(
      eventId = 1,
      timestamp = "2026-06-06T12:00:00Z",
      customerId = 1,
      amount = "100.00",
      currency = "PLN",
      status = "SUCCESS",
      paymentMethod = "card",
      transactionCountry = "PL",
      merchantCategory = "online",
      channel = "web",
      deviceId = "device-1",
      riskScore = 10,
      riskDecision = "Accept",
      finalDecision = "Accepted"
    )

    val alert =
      DashboardAlert(eventId = 1, customerId = 1, alertType = "CountryMismatch", riskScore = 5)
    val violation = DashboardViolation(eventId = 1, customerId = 1, violationType = "Eligibility")

    val dataset = DashboardDataset(
      generatedAt = java.time.Instant.now.toString,
      events = List(event),
      alerts = List(alert),
      violations = List(violation)
    )

    val json = dataset.asJson.noSpaces

    val parsed = parse(json).getOrElse(fail("invalid json"))
    val cursor = parsed.hcursor

    assert(cursor.downField("events").succeeded)
    assert(cursor.downField("alerts").succeeded)
    assert(cursor.downField("violations").succeeded)

    // Ensure hashedCustomerEmail and any email field not present
    assert(!json.contains("hashedCustomerEmail"))
    assert(!json.toLowerCase.contains("email"))
  }
