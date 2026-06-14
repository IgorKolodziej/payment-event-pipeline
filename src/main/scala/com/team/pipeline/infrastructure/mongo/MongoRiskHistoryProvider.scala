package com.team.pipeline.infrastructure.mongo

import cats.effect.IO
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.CustomerId.*
import com.team.pipeline.domain.EventId
import com.team.pipeline.domain.EventId.*
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.ports.{RiskHistoryEvent, RiskHistoryProvider}
import org.bson.Document

import java.time.Instant
import java.util.Date
import scala.jdk.CollectionConverters.*

final class MongoRiskHistoryProvider(
    collection: MongoCollection[Document]
) extends RiskHistoryProvider:

  override def historyFor(
      customerId: CustomerId,
      fromInclusive: Instant,
      toExclusive: Instant,
      excludeEventId: EventId
  ): IO[List[RiskHistoryEvent]] =
    val filter = Filters.and(
      Filters.eq("customerId", customerId.value),
      Filters.gte("timestamp", Date.from(fromInclusive)),
      Filters.lt("timestamp", Date.from(toExclusive)),
      Filters.ne("eventId", excludeEventId.value)
    )

    val projection = Projections.include(
      "eventId",
      "timestamp",
      "amount",
      "status",
      "paymentMethod",
      "deviceId",
      "finalDecision"
    )

    IO.blocking {
      collection
        .find(filter)
        .projection(projection)
        .into(new java.util.ArrayList[Document]())
        .asScala
        .toList
        .map(toRiskHistoryEvent)
    }

  private def toRiskHistoryEvent(doc: Document): RiskHistoryEvent =
    val eventId = EventId(doc.getInteger("eventId").intValue())
    val timestamp = doc.getDate("timestamp").toInstant
    val amount = BigDecimal(doc.getString("amount"))
    val status = parseStored("status", doc.getString("status"))(EventStatus.fromCode)
    val paymentMethod =
      parseStored("paymentMethod", doc.getString("paymentMethod"))(PaymentMethod.fromCode)
    val deviceId = doc.getString("deviceId")
    val finalDecision =
      parseStored("finalDecision", doc.getString("finalDecision"))(FinalDecision.fromCode)

    RiskHistoryEvent(
      eventId = eventId,
      timestamp = timestamp,
      amount = amount,
      status = status,
      paymentMethod = paymentMethod,
      deviceId = deviceId,
      finalDecision = finalDecision
    )

  private def parseStored[A](
      field: String,
      value: String
  )(parse: String => Either[String, A]): A =
    parse(value).fold(
      error => throw IllegalArgumentException(s"Invalid Mongo history field '$field': $error"),
      identity
    )
