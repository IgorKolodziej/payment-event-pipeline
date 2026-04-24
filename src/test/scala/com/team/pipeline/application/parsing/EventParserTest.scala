package com.team.pipeline.application.parsing

import com.team.pipeline.domain.InvalidJson
import com.team.pipeline.domain.MissingField
import com.team.pipeline.domain.RawPaymentEvent
import munit.FunSuite

class EventParserTest extends FunSuite:
  test("parses valid payment event line") {
    val line =
      """{"eventId":1,"timestamp":"2026-04-24T10:00:00Z","customerId":1,"amount":150.00,"currency":"PLN","status":"SUCCESS","paymentMethod":"BLIK","transactionCountry":"PL","merchantId":"M001","merchantCategory":"GROCERY","channel":"MOBILE","deviceId":"device-001"}"""

    assertEquals(
      EventParser.parseLine(line),
      Right(
        RawPaymentEvent(
          eventId = 1,
          timestamp = "2026-04-24T10:00:00Z",
          customerId = 1,
          amount = BigDecimal("150.00"),
          currency = "PLN",
          status = "SUCCESS",
          paymentMethod = "BLIK",
          transactionCountry = "PL",
          merchantId = "M001",
          merchantCategory = "GROCERY",
          channel = "MOBILE",
          deviceId = "device-001"
        )
      )
    )
  }

  test("parses transaction context fields") {
    val line =
      """{"eventId":19,"timestamp":"2026-04-24T11:30:00Z","customerId":44,"amount":5000.00,"currency":"EUR","status":"FAILED","paymentMethod":"TRANSFER","transactionCountry":"DE","merchantId":"M019","merchantCategory":"TRAVEL","channel":"WEB","deviceId":"device-044-1"}"""

    EventParser.parseLine(line) match
      case Right(parsed) =>
        assertEquals(parsed.currency, "EUR")
        assertEquals(parsed.status, "FAILED")
        assertEquals(parsed.paymentMethod, "TRANSFER")
        assertEquals(parsed.transactionCountry, "DE")
        assertEquals(parsed.merchantCategory, "TRAVEL")
        assertEquals(parsed.channel, "WEB")
        assertEquals(parsed.deviceId, "device-044-1")
      case Left(error) =>
        fail(s"Expected parseLine to succeed, but got: $error")
  }

  test("returns InvalidJson for malformed JSON") {
    val result = EventParser.parseLine("""{"eventId":1""")

    assert(result match
      case Left(InvalidJson(_)) => true
      case _                    => false)
  }

  test("returns MissingField for missing required field") {
    val line =
      """{"eventId":1,"timestamp":"2026-04-24T10:00:00Z","amount":150.00,"currency":"PLN","status":"SUCCESS","paymentMethod":"BLIK","transactionCountry":"PL","merchantId":"M001","merchantCategory":"GROCERY","channel":"MOBILE","deviceId":"device-001"}"""

    assertEquals(EventParser.parseLine(line), Left(MissingField("customerId")))
  }
end EventParserTest
