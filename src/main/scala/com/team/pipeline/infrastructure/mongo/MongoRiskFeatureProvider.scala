package com.team.pipeline.infrastructure.mongo

import cats.effect.IO
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.team.pipeline.application.risk.CustomerRiskContext
import com.team.pipeline.application.risk.RiskPolicy
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.FinalDecision
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.infrastructure.mongo.RiskContextComputation.HistoryEvent
import com.team.pipeline.ports.RiskFeatureProvider
import org.bson.Document

import java.time.Duration
import java.util.Date
import scala.jdk.CollectionConverters.*

final class MongoRiskFeatureProvider(
    collection: MongoCollection[Document],
    policy: RiskPolicy = RiskPolicy.default
) extends RiskFeatureProvider:

  override def contextFor(event: EnrichedPaymentEvent): IO[CustomerRiskContext] =
    val customerId = event.event.customerId
    val now = event.event.timestamp
    val from30d = now.minus(Duration.ofDays(30))

    val filter = Filters.and(
      Filters.eq("customerId", customerId),
      Filters.gte("timestamp", Date.from(from30d)),
      Filters.lt("timestamp", Date.from(now)),
      Filters.ne("eventId", event.event.eventId)
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
      val docs =
        collection
          .find(filter)
          .projection(projection)
          .into(new java.util.ArrayList[Document]())
          .asScala
          .toList

      val history = docs.map(toHistoryEvent)

      RiskContextComputation.compute(
        event,
        history,
        lateNightStartHour = policy.lateNightStartHour,
        lateNightEndHour = policy.lateNightEndHour
      )
    }

  private def toHistoryEvent(doc: Document): HistoryEvent =
    val eventId = doc.getInteger("eventId").intValue()
    val timestamp = doc.getDate("timestamp").toInstant
    val amount = BigDecimal(doc.getString("amount"))
    val status = parseStored("status", doc.getString("status"))(EventStatus.fromCode)
    val paymentMethod =
      parseStored("paymentMethod", doc.getString("paymentMethod"))(PaymentMethod.fromCode)
    val deviceId = doc.getString("deviceId")
    val finalDecision =
      parseStored("finalDecision", doc.getString("finalDecision"))(FinalDecision.fromCode)

    HistoryEvent(
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
