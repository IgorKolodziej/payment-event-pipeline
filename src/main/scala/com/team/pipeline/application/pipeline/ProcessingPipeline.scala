package com.team.pipeline.application.pipeline

import cats.data.NonEmptyChain
import cats.effect.IO
import com.team.pipeline.application.decision.PaymentDecisionEngine
import com.team.pipeline.application.enrichment.EventEnricher
import com.team.pipeline.application.parsing.EventParser
import com.team.pipeline.application.reporting.RunSummary
import com.team.pipeline.application.reporting.*
import com.team.pipeline.application.risk.RiskPolicy
import com.team.pipeline.application.validation.EmailHasher
import com.team.pipeline.application.validation.EventValidator
import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.DataError
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EventId
import com.team.pipeline.domain.PaymentAssessment
import com.team.pipeline.domain.ProcessedEvent
import com.team.pipeline.domain.RejectedEvent
import com.team.pipeline.ports.AlertStore
import com.team.pipeline.ports.CustomerProfileLookup
import com.team.pipeline.ports.EligibilityViolationStore
import com.team.pipeline.ports.EventSource
import com.team.pipeline.ports.ProcessedEventStore
import com.team.pipeline.ports.RiskFeatureProvider
import fs2.Stream

object ProcessingPipeline:
  final case class Dependencies(
      customerLookup: CustomerProfileLookup,
      riskFeatureProvider: RiskFeatureProvider,
      processedEventStore: ProcessedEventStore,
      eligibilityViolationStore: EligibilityViolationStore,
      alertStore: AlertStore,
      emailHasher: EmailHasher,
      riskPolicy: RiskPolicy
  )

  enum RecordOutcome:
    case Rejected(rejected: RejectedEvent)
    case Processed(processed: ProcessedEvent, assessment: PaymentAssessment)

  def process(
      records: Stream[IO, EventSource.InputRecord],
      dependencies: Dependencies
  ): Stream[IO, RecordOutcome] =
    records.evalMap(processRecord(_, dependencies))

  def run(
      records: Stream[IO, EventSource.InputRecord],
      dependencies: Dependencies
  ): IO[RunSummary] =
    process(records, dependencies).compile.fold(RunSummary.empty)(updateSummary)

  private[pipeline] def processRecord(
      record: EventSource.InputRecord,
      dependencies: Dependencies
  ): IO[RecordOutcome] =
    EventParser.parseLine(record.value) match
      case Left(error) =>
        IO.pure(rejected(record.sourcePosition, eventId = None, customerId = None, error))

      case Right(raw) =>
        EventValidator.validateAndNormalize(raw).toEither match
          case Left(errors) =>
            IO.pure(RecordOutcome.Rejected(EventValidator.toRejected(
              record.sourcePosition,
              raw,
              errors.map(identity[DataError])
            )))

          case Right(normalized) =>
            dependencies.customerLookup.find(normalized.customerId).flatMap { customer =>
              EventEnricher.enrichOption(normalized, customer, dependencies.emailHasher) match
                case Left(error) =>
                  IO.pure(
                    rejected(
                      record.sourcePosition,
                      eventId = Some(normalized.eventId),
                      customerId = Some(normalized.customerId),
                      error
                    )
                  )

                case Right(enriched) =>
                  processEnriched(enriched, dependencies)
            }

  private def processEnriched(
      event: EnrichedPaymentEvent,
      dependencies: Dependencies
  ): IO[RecordOutcome] =
    for
      context <- dependencies.riskFeatureProvider.contextFor(event)
      assessment = PaymentDecisionEngine.evaluate(event, context, dependencies.riskPolicy)
      processed = ProcessedEventMapper.from(event, assessment)
      alerts = assessment.risk.toList.flatMap(_.alerts)
      _ <- dependencies.processedEventStore.save(processed)
      _ <- dependencies.eligibilityViolationStore.saveAll(assessment.eligibility.violations)
      _ <- dependencies.alertStore.saveAll(alerts)
    yield RecordOutcome.Processed(processed, assessment)

  private def rejected(
      sourcePosition: Long,
      eventId: Option[EventId],
      customerId: Option[CustomerId],
      reason: DataError
  ): RecordOutcome =
    RecordOutcome.Rejected(
      RejectedEvent(
        sourcePosition = sourcePosition,
        eventId = eventId,
        customerId = customerId,
        reasons = NonEmptyChain.one(reason)
      )
    )

  private def updateSummary(summary: RunSummary, outcome: RecordOutcome): RunSummary =
    val afterRead = summary.onLineRead

    outcome match
      case RecordOutcome.Rejected(rejected) =>
        afterRead.onRejected(rejected.reasons)

      case RecordOutcome.Processed(processed, assessment) =>
        afterRead
          .onProcessed(processed.transactionCountry)
          .onDecision(assessment.finalDecision)
          .onAlerts(assessment.risk.toList.flatMap(_.alerts))
