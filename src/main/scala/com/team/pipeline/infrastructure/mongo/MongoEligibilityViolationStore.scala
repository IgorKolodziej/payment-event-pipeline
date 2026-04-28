package com.team.pipeline.infrastructure.mongo

import cats.effect.IO
import cats.syntax.all.*
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.team.pipeline.domain.EligibilityViolation
import com.team.pipeline.ports.EligibilityViolationStore
import org.bson.Document

final class MongoEligibilityViolationStore(collection: MongoCollection[Document])
    extends EligibilityViolationStore:

  private val upsert: ReplaceOptions = ReplaceOptions().upsert(true)

  override def saveAll(violations: List[EligibilityViolation]): IO[Unit] =
    violations.traverse_ { violation =>
      val filter = Filters.and(
        Filters.eq("eventId", violation.eventId),
        Filters.eq("violationType", violation.violationType.code)
      )
      val doc = MongoDocumentMapping.EligibilityViolationDoc.toDocument(violation)

      IO.blocking(collection.replaceOne(filter, doc, upsert)).void
    }
