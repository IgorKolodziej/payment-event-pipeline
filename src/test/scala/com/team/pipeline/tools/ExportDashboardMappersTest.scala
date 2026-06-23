package com.team.pipeline.tools

import munit.FunSuite
import org.bson.Document
import java.time.Instant
import java.util.Date
import io.circe.syntax._
import com.team.pipeline.dashboard.contract._

class ExportDashboardMappersTest extends FunSuite:
  import ExportDashboardMappers._

  test("docToEvent handles Date timestamp and numeric amount") {
    val ts = Instant.parse("2026-04-24T10:00:00Z")
    val doc = new Document()
    doc.put("eventId", Integer.valueOf(1))
    doc.put("timestamp", Date.from(ts))
    doc.put("customerId", Integer.valueOf(1))
    doc.put("amount", java.lang.Double.valueOf(150.0))
    doc.put("currency", "PLN")

    val ev = docToEvent(doc)

    assertEquals(ev.timestamp, ts.toString)
    assert(ev.amount.isInstanceOf[String])
    assertEquals(ev.amount, doc.get("amount").toString)
    // ensure no email fields leak into DTO JSON
    assert(!ev.asJson.noSpaces.toLowerCase.contains("email"))
  }

  test("docToEvent handles string timestamp and integer amount") {
    val tsStr = "2026-04-24T10:05:00Z"
    val doc = new Document()
    doc.put("eventId", Integer.valueOf(2))
    doc.put("timestamp", tsStr)
    doc.put("customerId", Integer.valueOf(2))
    doc.put("amount", Integer.valueOf(1200))
    doc.put("currency", "EUR")

    val ev = docToEvent(doc)

    assertEquals(ev.timestamp, tsStr)
    assert(ev.amount.isInstanceOf[String])
    assertEquals(ev.amount, doc.get("amount").toString)
  }

  test("docToEvent handles long timestamp (epoch millis)") {
    val ts = Instant.parse("2026-04-24T10:15:00Z")
    val epoch = java.lang.Long.valueOf(ts.toEpochMilli)
    val doc = new Document()
    doc.put("eventId", Integer.valueOf(4))
    doc.put("timestamp", epoch)
    doc.put("customerId", Integer.valueOf(4))
    doc.put("amount", "5000.00")
    doc.put("currency", "USD")

    val ev = docToEvent(doc)

    assertEquals(ev.timestamp, ts.toString)
    assert(ev.amount.isInstanceOf[String])
    assertEquals(ev.amount, doc.get("amount").toString)
  }

  test("docToAlert and docToViolation mapping") {
    val alertDoc = new Document()
    alertDoc.put("eventId", Integer.valueOf(2))
    alertDoc.put("customerId", Integer.valueOf(2))
    alertDoc.put("alertType", "CountryMismatch")
    alertDoc.put("riskScore", Integer.valueOf(15))

    val alert = docToAlert(alertDoc)
    assertEquals(alert.eventId, 2)
    assertEquals(alert.customerId, 2)
    assertEquals(alert.alertType, "CountryMismatch")
    assertEquals(alert.riskScore, 15)

    val vDoc = new Document()
    vDoc.put("eventId", Integer.valueOf(4))
    vDoc.put("customerId", Integer.valueOf(4))
    vDoc.put("violationType", "InactiveCustomer")

    val v = docToViolation(vDoc)
    assertEquals(v.eventId, 4)
    assertEquals(v.customerId, 4)
    assertEquals(v.violationType, "InactiveCustomer")
  }
