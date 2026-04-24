package com.team.pipeline.application.validation

import cats.data.ValidatedNec
import cats.syntax.all.*
import com.team.pipeline.domain.InvalidAmount
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.RawPaymentEvent
import com.team.pipeline.domain.ValidationError

object EventValidator:
  def validateAndNormalize(
      raw: RawPaymentEvent
  ): ValidatedNec[ValidationError, NormalizedPaymentEvent] =
    (
      validateTimestamp(raw),
      validateAmount(raw),
      validateStatus(raw),
      validatePaymentMethods(raw)
    ).mapN { case (timestamp, amount, status, paymentMethods) =>
      NormalizedPaymentEvent(
        eventId = raw.eventId,
        timestamp = timestamp,
        customerId = raw.customerId,
        amount = amount,
        status = status,
        paymentMethods = paymentMethods
      )
    }

  private def validateTimestamp(raw: RawPaymentEvent) =
    EventNormalizer.normalizeTimestamp(raw.timestamp).toValidatedNec

  private def validateAmount(raw: RawPaymentEvent) =
    if raw.amount > 0 then raw.amount.validNec
    else InvalidAmount(raw.amount).invalidNec

  private def validateStatus(raw: RawPaymentEvent) =
    EventNormalizer.normalizeStatus(raw.status).toValidatedNec

  private def validatePaymentMethods(raw: RawPaymentEvent) =
    EventNormalizer
      .normalizePaymentMethodFlags(raw.hasBlik, raw.hasCard, raw.hasTransfer)
      .toValidatedNec
