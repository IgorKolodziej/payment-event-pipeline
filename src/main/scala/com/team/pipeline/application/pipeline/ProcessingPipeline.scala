package com.team.pipeline.application.pipeline

import cats.effect.IO
import com.team.pipeline.application.decision.PaymentDecisionEngine
import com.team.pipeline.application.enrichment.EventEnricher
import com.team.pipeline.application.parsing.EventParser
import com.team.pipeline.application.reporting.RunSummary
import com.team.pipeline.application.reporting.*
import com.team.pipeline.application.risk.RiskPolicy
import com.team.pipeline.application.validation.EmailHasher
import com.team.pipeline.application.validation.EventValidator
import com.team.pipeline.domain.DataError
import com.team.pipeline.domain.EnrichedPaymentEvent
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

  enum LineOutcome:
    case Rejected(rejected: RejectedEvent)
    case Processed(processed: ProcessedEvent, assessment: PaymentAssessment)

  def process(
      lines: Stream[IO, EventSource.InputLine],
      dependencies: Dependencies
  ): Stream[IO, LineOutcome] =
    lines.evalMap(processLine(_, dependencies))

  def run(
      lines: Stream[IO, EventSource.InputLine],
      dependencies: Dependencies
  ): IO[RunSummary] =
    process(lines, dependencies).compile.fold(RunSummary.empty)(updateSummary)

  private[pipeline] def processLine(
      line: EventSource.InputLine,
      dependencies: Dependencies
  ): IO[LineOutcome] =
    EventParser.parseLine(line.value) match
      case Left(error) =>
        IO.pure(rejected(line.lineNumber, eventId = None, customerId = None, error))

      case Right(raw) =>
        EventValidator.validateAndNormalize(raw).toEither match
          case Left(errors) =>
            IO.pure(LineOutcome.Rejected(EventValidator.toRejected(
              line.lineNumber,
              raw,
              errors.head
            )))

          case Right(normalized) =>
            dependencies.customerLookup.find(normalized.customerId).flatMap { customer =>
              EventEnricher.enrichOption(normalized, customer, dependencies.emailHasher) match
                case Left(error) =>
                  IO.pure(
                    rejected(
                      line.lineNumber,
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
  ): IO[LineOutcome] =
    for
      context <- dependencies.riskFeatureProvider.contextFor(event)
      assessment = PaymentDecisionEngine.evaluate(event, context, dependencies.riskPolicy)
      processed = ProcessedEventMapper.from(event, assessment)
      alerts = assessment.risk.toList.flatMap(_.alerts)
      _ <- dependencies.processedEventStore.save(processed)
      _ <- dependencies.eligibilityViolationStore.saveAll(assessment.eligibility.violations)
      _ <- dependencies.alertStore.saveAll(alerts)
    yield LineOutcome.Processed(processed, assessment)

  private def rejected(
      lineNumber: Long,
      eventId: Option[Int],
      customerId: Option[Int],
      reason: DataError
  ): LineOutcome =
    LineOutcome.Rejected(
      RejectedEvent(
        lineNumber = lineNumber,
        eventId = eventId,
        customerId = customerId,
        reason = reason
      )
    )

  private def updateSummary(summary: RunSummary, outcome: LineOutcome): RunSummary =
    val afterRead = summary.onLineRead

    outcome match
      case LineOutcome.Rejected(rejected) =>
        afterRead.onRejected(rejected.reason)

      case LineOutcome.Processed(processed, assessment) =>
        afterRead
          .onProcessed(processed.transactionCountry)
          .onDecision(assessment.finalDecision)
          .onAlerts(assessment.risk.toList.flatMap(_.alerts))
