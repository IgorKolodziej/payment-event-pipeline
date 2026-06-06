package com.team.pipeline.dashboard.contract

import io.circe.parser.{decode, parse}
import io.circe.syntax._
import munit.FunSuite

class DashboardDatasetTest extends FunSuite:

  private val sampleEvent =
    DashboardEvent(
      eventId = 100,
      timestamp = "2026-04-24T10:00:00Z",
      customerId = 1,
      amount = "150.00",
      currency = "PLN",
      status = "SUCCESS",
      paymentMethod = "BLIK",
      transactionCountry = "PL",
      merchantCategory = "GROCERY",
      channel = "MOBILE",
      deviceId = "device-1-1",
      riskScore = 20,
      riskDecision = "Review",
      finalDecision = "Review"
    )

  private val sampleDataset =
    DashboardDataset(
      generatedAt = "2026-05-08T12:00:00Z",
      events = List(sampleEvent),
      alerts = List(DashboardAlert(100, 1, "VelocitySpike", 10)),
      violations = List(DashboardViolation(101, 1, "DailyLimitExceeded"))
    )

  test("encoded dataset JSON must not leak hashedCustomerEmail or raw email") {
    val json = sampleDataset.asJson.noSpaces
    assert(!json.contains("hashedCustomerEmail"), "dataset must not expose hashedCustomerEmail")
    assert(!json.toLowerCase.contains("email"), "dataset must not expose any email field")
  }

  test("dataset JSON exposes the minimum top-level fields") {
    val cursor = sampleDataset.asJson.hcursor
    assert(cursor.downField("generatedAt").succeeded)
    assert(cursor.downField("events").succeeded)
    assert(cursor.downField("alerts").succeeded)
    assert(cursor.downField("violations").succeeded)
  }

  test("event JSON exposes the minimum fields needed for filters and charts") {
    val eventCursor = sampleEvent.asJson.hcursor
    val requiredFields = List(
      "eventId",
      "timestamp",
      "customerId",
      "amount",
      "currency",
      "status",
      "paymentMethod",
      "transactionCountry",
      "merchantCategory",
      "channel",
      "deviceId",
      "riskScore",
      "riskDecision",
      "finalDecision"
    )
    requiredFields.foreach { field =>
      assert(eventCursor.downField(field).succeeded, s"missing required event field: $field")
    }
  }

  test("amount is encoded as a JSON string (no decimal precision surprises)") {
    val json = parse(sampleEvent.asJson.noSpaces).getOrElse(fail("invalid json"))
    val amount = json.hcursor.get[String]("amount")
    assertEquals(amount, Right("150.00"))
  }

  test("dataset round-trips through encode/decode") {
    val decoded = decode[DashboardDataset](sampleDataset.asJson.noSpaces)
    assertEquals(decoded, Right(sampleDataset))
  }
