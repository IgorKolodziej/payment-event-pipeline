package com.team.pipeline.infrastructure.mongo

import cats.effect.IO
import cats.syntax.all.*
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.team.pipeline.domain.Alert
import com.team.pipeline.domain.EventId.*
import com.team.pipeline.ports.AlertStore
import org.bson.Document

final class MongoAlertStore(collection: MongoCollection[Document])
    extends AlertStore:

  private val upsert: ReplaceOptions = ReplaceOptions().upsert(true)

  override def saveAll(alerts: List[Alert]): IO[Unit] =
    alerts.traverse_ { alert =>
      val filter = Filters.and(
        Filters.eq("eventId", alert.eventId.value),
        Filters.eq("alertType", alert.alertType.code)
      )
      val doc = MongoDocumentMapping.AlertDoc.toDocument(alert)

      IO.blocking(collection.replaceOne(filter, doc, upsert)).void
    }
