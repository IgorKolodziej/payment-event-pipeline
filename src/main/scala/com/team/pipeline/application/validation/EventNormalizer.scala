package com.team.pipeline.application.validation

import com.team.pipeline.domain.{
  EventStatus,
  InvalidPaymentMethodFlags,
  InvalidStatus,
  InvalidTimestamp,
  NoPaymentMethodSelected,
  NormalizedPaymentEvent,
  PaymentMethod,
  RawPaymentEvent,
  ValidationError
}

import java.time.Instant
import scala.util.Try

/** Normalizes payment events from raw ingestion format into strongly typed domain types.
  *
  * This component is intentionally pure (no IO) so it can be used in FS2 streams, unit tests, and
  * higher-level validators.
  */
trait EventNormalizer:
  def normalize(raw: RawPaymentEvent): Either[ValidationError, NormalizedPaymentEvent]

object EventNormalizer:
  val default: EventNormalizer = new EventNormalizer:
    override def normalize(
        raw: RawPaymentEvent
    ): Either[ValidationError, NormalizedPaymentEvent] =
      for
        ts <- normalizeTimestamp(raw.timestamp)
        status <- normalizeStatus(raw.status)
        methods <- normalizePaymentMethodFlags(raw.hasBlik, raw.hasCard, raw.hasTransfer)
      yield NormalizedPaymentEvent(
        eventId = raw.eventId,
        timestamp = ts,
        customerId = raw.customerId,
        amount = raw.amount,
        status = status,
        paymentMethods = methods
      )

  def normalizeTimestamp(value: String): Either[ValidationError, Instant] =
    Try(Instant.parse(value)).toEither.left.map(_ => InvalidTimestamp(value))

  def normalizeStatus(value: Int): Either[ValidationError, EventStatus] =
    value match
      case 1     => Right(EventStatus.Success)
      case 0     => Right(EventStatus.Failed)
      case other => Left(InvalidStatus(other))

  def normalizePaymentMethodFlags(
      hasBlik: Int,
      hasCard: Int,
      hasTransfer: Int
  ): Either[ValidationError, Set[PaymentMethod]] =
    def isBoolFlag(v: Int): Boolean = v == 0 || v == 1

    if !isBoolFlag(hasBlik) || !isBoolFlag(hasCard) || !isBoolFlag(hasTransfer) then
      Left(InvalidPaymentMethodFlags(hasBlik, hasCard, hasTransfer))
    else
      val methods = Set(
        Option.when(hasBlik == 1)(PaymentMethod.Blik),
        Option.when(hasCard == 1)(PaymentMethod.Card),
        Option.when(hasTransfer == 1)(PaymentMethod.Transfer)
      ).flatten

      Either.cond(methods.nonEmpty, methods, NoPaymentMethodSelected)
