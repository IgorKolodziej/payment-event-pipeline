package com.team.pipeline.infrastructure.mongo

import cats.effect.IO
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.team.pipeline.domain.EventId.*
import com.team.pipeline.domain.ProcessedEvent
import com.team.pipeline.ports.ProcessedEventStore
import org.bson.Document

final class MongoProcessedEventStore(collection: MongoCollection[Document])
    extends ProcessedEventStore:

  private val upsert: ReplaceOptions = ReplaceOptions().upsert(true)

  override def save(event: ProcessedEvent): IO[Unit] =
    val filter = Filters.eq("eventId", event.eventId.value)
    val doc = MongoDocumentMapping.ProcessedEventDoc.toDocument(event)

    IO.blocking(collection.replaceOne(filter, doc, upsert)).void
