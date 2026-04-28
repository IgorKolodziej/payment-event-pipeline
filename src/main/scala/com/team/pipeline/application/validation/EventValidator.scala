package com.team.pipeline.application.validation

import cats.data.ValidatedNec
import cats.syntax.all.*
import com.team.pipeline.domain.DataError
import com.team.pipeline.domain.InvalidAmount
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.RawPaymentEvent
import com.team.pipeline.domain.RejectedEvent
import com.team.pipeline.domain.ValidationError

object EventValidator:
  def validateAndNormalize(
      raw: RawPaymentEvent
  ): ValidatedNec[ValidationError, NormalizedPaymentEvent] =
    (
      validateTimestamp(raw),
      validateAmount(raw),
      validateCurrency(raw),
      validateStatus(raw),
      validatePaymentMethod(raw),
      validateTransactionCountry(raw),
      validateMerchantCategory(raw),
      validateChannel(raw)
    ).mapN {
      case (
            timestamp,
            amount,
            currency,
            status,
            paymentMethod,
            transactionCountry,
            merchantCategory,
            channel
          ) =>
        NormalizedPaymentEvent(
          eventId = raw.eventId,
          timestamp = timestamp,
          customerId = raw.customerId,
          amount = amount,
          currency = currency,
          status = status,
          paymentMethod = paymentMethod,
          transactionCountry = transactionCountry,
          merchantId = raw.merchantId.trim,
          merchantCategory = merchantCategory,
          channel = channel,
          deviceId = raw.deviceId.trim
        )
    }

  def toRejected(
      sourcePosition: Long,
      raw: RawPaymentEvent,
      reason: DataError
  ): RejectedEvent =
    RejectedEvent(
      sourcePosition = sourcePosition,
      eventId = Some(raw.eventId),
      customerId = Some(raw.customerId),
      reason = reason
    )

  private def validateTimestamp(raw: RawPaymentEvent) =
    EventNormalizer.normalizeTimestamp(raw.timestamp).toValidatedNec

  private def validateAmount(raw: RawPaymentEvent) =
    if raw.amount > 0 then raw.amount.validNec
    else InvalidAmount(raw.amount).invalidNec

  private def validateCurrency(raw: RawPaymentEvent) =
    EventNormalizer.normalizeCurrency(raw.currency).toValidatedNec

  private def validateStatus(raw: RawPaymentEvent) =
    EventNormalizer.normalizeStatus(raw.status).toValidatedNec

  private def validatePaymentMethod(raw: RawPaymentEvent) =
    EventNormalizer.normalizePaymentMethod(raw.paymentMethod).toValidatedNec

  private def validateTransactionCountry(raw: RawPaymentEvent) =
    EventNormalizer.normalizeCountry(raw.transactionCountry).toValidatedNec

  private def validateMerchantCategory(raw: RawPaymentEvent) =
    EventNormalizer.normalizeMerchantCategory(raw.merchantCategory).toValidatedNec

  private def validateChannel(raw: RawPaymentEvent) =
    EventNormalizer.normalizeChannel(raw.channel).toValidatedNec
