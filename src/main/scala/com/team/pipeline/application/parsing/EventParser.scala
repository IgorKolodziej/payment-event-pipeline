package com.team.pipeline.application.parsing

import com.team.pipeline.domain.InvalidJson
import com.team.pipeline.domain.MissingField
import com.team.pipeline.domain.ParseError
import com.team.pipeline.domain.RawPaymentEvent
import io.circe.CursorOp.DownField
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Error
import io.circe.ParsingFailure
import io.circe.parser.decode

object EventParser:
  given Decoder[RawPaymentEvent] =
    Decoder.forProduct8(
      "eventId",
      "timestamp",
      "customerId",
      "amount",
      "status",
      "has_blik",
      "has_card",
      "has_transfer"
    )(RawPaymentEvent.apply)

  def parseLine(line: String): Either[ParseError, RawPaymentEvent] =
    decode[RawPaymentEvent](line).left.map(toParseError)

  private def toParseError(error: Error): ParseError =
    error match
      case parsingFailure: ParsingFailure =>
        InvalidJson(parsingFailure.message)
      case decodingFailure: DecodingFailure =>
        missingField(decodingFailure).getOrElse(InvalidJson(decodingFailure.message))

  private def missingField(error: DecodingFailure): Option[MissingField] =
    error.message match
      case message if message.startsWith("Missing required field") =>
        error.pathToRoot.collectFirst { case DownField(field) =>
          MissingField(field)
        }
      case _ =>
        None
