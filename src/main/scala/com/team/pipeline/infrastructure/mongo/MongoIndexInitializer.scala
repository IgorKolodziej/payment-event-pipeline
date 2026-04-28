package com.team.pipeline.infrastructure.mongo

import cats.effect.IO
import cats.syntax.all.*
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.team.pipeline.config.MongoConfig
import org.bson.conversions.Bson

object MongoIndexInitializer:
  private[mongo] final case class IndexDefinition(
      collectionName: String,
      fields: List[String],
      unique: Boolean,
      name: String
  ):
    def keys: Bson =
      Indexes.ascending(fields*)

    def options: IndexOptions =
      IndexOptions()
        .name(name)
        .unique(unique)

  def initialize(database: MongoDatabase, config: MongoConfig): IO[Unit] =
    definitions(config).traverse_(createIndex(database, _))

  private[mongo] def definitions(config: MongoConfig): List[IndexDefinition] =
    List(
      IndexDefinition(
        collectionName = config.processedCollection,
        fields = List("eventId"),
        unique = true,
        name = "eventId_1"
      ),
      IndexDefinition(
        collectionName = config.processedCollection,
        fields = List("customerId", "timestamp"),
        unique = false,
        name = "customerId_1_timestamp_1"
      ),
      IndexDefinition(
        collectionName = config.processedCollection,
        fields = List("customerId", "deviceId"),
        unique = false,
        name = "customerId_1_deviceId_1"
      ),
      IndexDefinition(
        collectionName = config.violationsCollection,
        fields = List("eventId", "violationType"),
        unique = true,
        name = "eventId_1_violationType_1"
      ),
      IndexDefinition(
        collectionName = config.violationsCollection,
        fields = List("customerId"),
        unique = false,
        name = "customerId_1"
      ),
      IndexDefinition(
        collectionName = config.alertsCollection,
        fields = List("eventId", "alertType"),
        unique = true,
        name = "eventId_1_alertType_1"
      ),
      IndexDefinition(
        collectionName = config.alertsCollection,
        fields = List("customerId"),
        unique = false,
        name = "customerId_1"
      )
    )

  private def createIndex(database: MongoDatabase, definition: IndexDefinition): IO[Unit] =
    IO.blocking {
      database
        .getCollection(definition.collectionName)
        .createIndex(definition.keys, definition.options)
      ()
    }
