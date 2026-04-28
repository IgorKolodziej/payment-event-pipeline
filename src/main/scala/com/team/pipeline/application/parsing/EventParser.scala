package com.team.pipeline.application.parsing

import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.EventId
import com.team.pipeline.domain.InvalidJson
import com.team.pipeline.domain.MissingField
import com.team.pipeline.domain.ParseError
import com.team.pipeline.domain.RawPaymentEvent
import io.circe.CursorOp.DownField
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.DecodingFailure.Reason
import io.circe.Error
import io.circe.ParsingFailure
import io.circe.parser.decode

object EventParser:
  private given Decoder[RawPaymentEvent] =
    Decoder.instance { cursor =>
      for
        eventId <- cursor.downField("eventId").as[Int]
        timestamp <- cursor.downField("timestamp").as[String]
        customerId <- cursor.downField("customerId").as[Int]
        amount <- cursor.downField("amount").as[BigDecimal]
        currency <- cursor.downField("currency").as[String]
        status <- cursor.downField("status").as[String]
        paymentMethod <- cursor.downField("paymentMethod").as[String]
        transactionCountry <- cursor.downField("transactionCountry").as[String]
        merchantId <- cursor.downField("merchantId").as[String]
        merchantCategory <- cursor.downField("merchantCategory").as[String]
        channel <- cursor.downField("channel").as[String]
        deviceId <- cursor.downField("deviceId").as[String]
      yield RawPaymentEvent(
        eventId = EventId(eventId),
        timestamp = timestamp,
        customerId = CustomerId(customerId),
        amount = amount,
        currency = currency,
        status = status,
        paymentMethod = paymentMethod,
        transactionCountry = transactionCountry,
        merchantId = merchantId,
        merchantCategory = merchantCategory,
        channel = channel,
        deviceId = deviceId
      )
    }

  def parseLine(line: String): Either[ParseError, RawPaymentEvent] =
    decode[RawPaymentEvent](line).left.map(toParseError)

  private def toParseError(error: Error): ParseError =
    error match
      case parsingFailure: ParsingFailure =>
        InvalidJson(parsingFailure.message)
      case decodingFailure: DecodingFailure =>
        missingField(decodingFailure).getOrElse(InvalidJson(decodingFailure.message))

  private def missingField(error: DecodingFailure): Option[MissingField] =
    error.reason match
      case Reason.MissingField =>
        error.history.collectFirst { case DownField(field) =>
          MissingField(field)
        }
      case _ =>
        None
