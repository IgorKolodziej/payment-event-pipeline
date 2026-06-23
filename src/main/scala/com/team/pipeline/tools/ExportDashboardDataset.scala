package com.team.pipeline.tools

/** Skeleton exporter for dashboard dataset.
  *   - Run with: sbt "runMain com.team.pipeline.tools.ExportDashboardDataset --since 2026-01-01
  *     --limit 1000"
  *   - This file is intentionally a safe skeleton that compiles. Fill mapping and DTOs when ready.
  */
object ExportDashboardDataset:
  def main(args: Array[String]): Unit =
    // Simple CLI parsing (very small) - extend as needed
    val argMap = args.sliding(2, 2).collect { case Array(k, v) => k -> v }.toMap
    val since = argMap.getOrElse("--since", "1970-01-01T00:00:00Z")
    val limitOpt = argMap.get("--limit").flatMap(s =>
      try Some(s.toInt)
      catch case _: Throwable => None
    )

    println(
      s"ExportDashboardDataset starting with since=$since limit=${limitOpt.getOrElse("none")}"
    )

    // Load configuration
    import com.team.pipeline.config.AppConfig
    import cats.effect.unsafe.implicits.global
    val cfg = AppConfig.load.unsafeRunSync()

    val mongoHost = cfg.mongo.host
    val mongoPort = cfg.mongo.port
    val mongoDb = cfg.mongo.database
    val processedCollName = cfg.mongo.processedCollection
    val alertsCollName = cfg.mongo.alertsCollection
    val violationsCollName = cfg.mongo.violationsCollection

    // Create Mongo client
    import com.mongodb.client.MongoClients
    import com.mongodb.client.model.Filters
    import com.mongodb.client.model.Projections
    import org.bson.Document
    import java.time.Instant
    import java.util.Date

    val connectionString = s"mongodb://$mongoHost:$mongoPort"
    val client = MongoClients.create(connectionString)

    try
      val db = client.getDatabase(mongoDb)
      val processedColl = db.getCollection(processedCollName)
      val alertsColl = db.getCollection(alertsCollName)
      val violationsColl = db.getCollection(violationsCollName)

      // Build filter for since (timestamp stored as Date)
      val sinceInstant =
        try Instant.parse(since)
        catch case _: Throwable => Instant.parse("1970-01-01T00:00:00Z")
      val sinceDate = Date.from(sinceInstant)
      val filter = Filters.gte("timestamp", sinceDate)

      // Prepare iterables
      val processedFind = limitOpt match
        case Some(l) => processedColl.find(filter).projection(Projections.exclude(
            "rawEmail",
            "hashedCustomerEmail"
          )).limit(l)
        case None => processedColl.find(filter).projection(Projections.exclude(
            "rawEmail",
            "hashedCustomerEmail"
          ))

      val alertsFind =
        alertsColl.find().projection(Projections.exclude("rawEmail", "hashedCustomerEmail"))
      val violationsFind = violationsColl.find()

      // Use canonical dashboard contract types and encoders
      import com.team.pipeline.dashboard.contract._
      import com.team.pipeline.reporting.JsonWriters
      import java.nio.file.Paths
      import scala.jdk.CollectionConverters.*

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
          case d: java.lang.Integer => Some(d.doubleValue())
          case s: String            =>
            try Some(s.toDouble)
            catch
              case _: Throwable => None
              case _            => None
        }

      def docToEvent(doc: Document): DashboardEvent =
        // eventId and customerId prefer numeric types; fallback to 0
        val eventId = asOptInt(
          doc,
          "eventId"
        ).getOrElse(Option(doc.getObjectId("_id")).map(_ => 0).getOrElse(0))
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

      // Collect results (for now load into memory; can be refactored to stream)
      val events = processedFind.iterator().asScala.map(docToEvent).toList
      val alerts = alertsFind.iterator().asScala.map(docToAlert).toList
      val violations = violationsFind.iterator().asScala.map(docToViolation).toList

      val dataset = DashboardDataset(
        generatedAt = Instant.now.toString,
        events = events,
        alerts = alerts,
        violations = violations
      )

      val outPath = Paths.get(cfg.app.outputDir.toString).resolve("dashboard_dataset.json")
      JsonWriters.writeJsonToFile(dataset, outPath)

      println(
        s"ExportDashboardDataset: wrote ${events.size} events, ${alerts.size} alerts, ${violations.size} violations to $outPath"
      )
    finally
      client.close()

    println("ExportDashboardDataset: finished")
    // Exit normally
    ()
