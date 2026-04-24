package com.team.pipeline.application.parsing

import com.team.pipeline.domain.InvalidJson
import com.team.pipeline.domain.MissingField
import com.team.pipeline.domain.RawPaymentEvent
import munit.FunSuite

class EventParserTest extends FunSuite:
  test("parses valid payment event line") {
    val line =
      """{"eventId":1,"timestamp":"2026-04-24T10:00:00Z","customerId":1,"amount":150.00,"status":0,"has_blik":1,"has_card":0,"has_transfer":0}"""

    assertEquals(
      EventParser.parseLine(line),
      Right(
        RawPaymentEvent(
          eventId = 1,
          timestamp = "2026-04-24T10:00:00Z",
          customerId = 1,
          amount = BigDecimal("150.00"),
          status = 0,
          hasBlik = 1,
          hasCard = 0,
          hasTransfer = 0
        )
      )
    )
  }

  test("maps snake_case payment method fields to camelCase domain fields") {
    val line =
      """{"eventId":19,"timestamp":"2026-04-24T11:30:00Z","customerId":44,"amount":5000.00,"status":0,"has_blik":1,"has_card":1,"has_transfer":0}"""

    EventParser.parseLine(line) match
      case Right(parsed) =>
        assertEquals(parsed.hasBlik, 1)
        assertEquals(parsed.hasCard, 1)
        assertEquals(parsed.hasTransfer, 0)
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
      """{"eventId":1,"timestamp":"2026-04-24T10:00:00Z","amount":150.00,"status":0,"has_blik":1,"has_card":0,"has_transfer":0}"""

    assertEquals(EventParser.parseLine(line), Left(MissingField("customerId")))
  }
end EventParserTest
