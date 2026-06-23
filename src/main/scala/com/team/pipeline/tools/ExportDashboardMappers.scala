package com.team.pipeline.tools

import org.bson.Document
import com.team.pipeline.dashboard.contract._
import java.time.Instant
import java.util.Date

object ExportDashboardMappers:
  def asOptString(doc: Document, key: String): Option[String] =
    Option(doc.get(key)).flatMap {
      case s: String => Some(s)
      case _         => Option(doc.getString(key))
    }

  def asOptInt(doc: Document, key: String): Option[Int] =
    Option(doc.get(key)).flatMap {
      case i: java.lang.Integer => Some(i.intValue())
      case l: java.lang.Long    => Some(l.intValue())
      case d: java.lang.Double  => Some(d.intValue())
      case s: String            =>
        try Some(s.toInt)
        catch
          case _: Throwable => None
          case _            => None
    }

  def asOptDouble(doc: Document, key: String): Option[Double] =
    Option(doc.get(key)).flatMap {
      case d: java.lang.Double  => Some(d.doubleValue())
      case i: java.lang.Integer => Some(i.doubleValue())
      case l: java.lang.Long    => Some(l.doubleValue())
      case s: String            =>
        try Some(s.toDouble)
        catch
          case _: Throwable => None
          case _            => None
    }

  def docToEvent(doc: Document): DashboardEvent =
    val eventId =
      asOptInt(doc, "eventId").getOrElse(Option(doc.getObjectId("_id")).map(_ => 0).getOrElse(0))
    val customerId = asOptInt(doc, "customerId").getOrElse(0)

    val timestampStr = Option(doc.get("timestamp")) match
      case Some(d: Date)   => Instant.ofEpochMilli(d.getTime).toString
      case Some(s: String) =>
        try Instant.parse(s).toString
        catch
          case _: Throwable            => s
          case Some(l: java.lang.Long) => Instant.ofEpochMilli(l.longValue()).toString
          case _                       => Instant.now.toString

    val amountStr = Option(doc.get("amount")).map(_.toString).getOrElse("0.00")

    val currency =
      asOptString(doc, "currency").getOrElse(asOptString(doc, "currencyCode").getOrElse(""))
    val status = asOptString(doc, "status").getOrElse("")
    val paymentMethod = asOptString(doc, "paymentMethod").getOrElse("")
    val transactionCountry = asOptString(doc, "transactionCountry").getOrElse("")
    val merchantCategory = asOptString(doc, "merchantCategory").getOrElse("")
    val channel = asOptString(doc, "channel").getOrElse("")
    val deviceId = asOptString(doc, "deviceId").getOrElse("")

    val riskScoreInt =
      asOptInt(doc, "riskScore").orElse(asOptDouble(doc, "riskScore").map(_.toInt)).getOrElse(0)
    val riskDecision = asOptString(doc, "riskDecision").getOrElse("")
    val finalDecision = asOptString(doc, "finalDecision").getOrElse("")

    DashboardEvent(
      eventId = eventId,
      timestamp = timestampStr,
      customerId = customerId,
      amount = amountStr,
      currency = currency,
      status = status,
      paymentMethod = paymentMethod,
      transactionCountry = transactionCountry,
      merchantCategory = merchantCategory,
      channel = channel,
      deviceId = deviceId,
      riskScore = riskScoreInt,
      riskDecision = riskDecision,
      finalDecision = finalDecision
    )

  def docToAlert(doc: Document): DashboardAlert =
    val eventId = asOptInt(doc, "eventId").getOrElse(0)
    val customerId = asOptInt(doc, "customerId").getOrElse(0)
    val alertType = asOptString(doc, "alertType").getOrElse("")
    val riskScore =
      asOptInt(doc, "riskScore").orElse(asOptDouble(doc, "riskScore").map(_.toInt)).getOrElse(0)
    DashboardAlert(eventId, customerId, alertType, riskScore)

  def docToViolation(doc: Document): DashboardViolation =
    val eventId = asOptInt(doc, "eventId").getOrElse(0)
    val customerId = asOptInt(doc, "customerId").getOrElse(0)
    val violationType = asOptString(doc, "violationType").getOrElse("")
    DashboardViolation(eventId, customerId, violationType)
