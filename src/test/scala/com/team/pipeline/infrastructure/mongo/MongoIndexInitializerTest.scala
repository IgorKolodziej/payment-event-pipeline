package com.team.pipeline.infrastructure.mongo

import com.team.pipeline.config.MongoConfig
import munit.FunSuite

class MongoIndexInitializerTest extends FunSuite:
  test("definitions include required named indexes") {
    val definitions = MongoIndexInitializer.definitions(config)

    assertEquals(
      definitions,
      List(
        MongoIndexInitializer.IndexDefinition(
          collectionName = "processed_transactions",
          fields = List("eventId"),
          unique = true,
          name = "eventId_1"
        ),
        MongoIndexInitializer.IndexDefinition(
          collectionName = "processed_transactions",
          fields = List("customerId", "timestamp"),
          unique = false,
          name = "customerId_1_timestamp_1"
        ),
        MongoIndexInitializer.IndexDefinition(
          collectionName = "processed_transactions",
          fields = List("customerId", "deviceId"),
          unique = false,
          name = "customerId_1_deviceId_1"
        ),
        MongoIndexInitializer.IndexDefinition(
          collectionName = "eligibility_violations",
          fields = List("eventId", "violationType"),
          unique = true,
          name = "eventId_1_violationType_1"
        ),
        MongoIndexInitializer.IndexDefinition(
          collectionName = "eligibility_violations",
          fields = List("customerId"),
          unique = false,
          name = "customerId_1"
        ),
        MongoIndexInitializer.IndexDefinition(
          collectionName = "alerts",
          fields = List("eventId", "alertType"),
          unique = true,
          name = "eventId_1_alertType_1"
        ),
        MongoIndexInitializer.IndexDefinition(
          collectionName = "alerts",
          fields = List("customerId"),
          unique = false,
          name = "customerId_1"
        )
      )
    )
  }

  private val config = MongoConfig(
    host = "localhost",
    port = 27017,
    database = "payment_pipeline",
    processedCollection = "processed_transactions",
    alertsCollection = "alerts",
    violationsCollection = "eligibility_violations"
  )
end MongoIndexInitializerTest
