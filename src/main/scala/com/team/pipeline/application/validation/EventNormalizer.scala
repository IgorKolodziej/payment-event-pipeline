package com.team.pipeline.application.validation

import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.EventStatus
import com.team.pipeline.domain.InvalidCurrency
import com.team.pipeline.domain.InvalidMerchantCategory
import com.team.pipeline.domain.InvalidPaymentChannel
import com.team.pipeline.domain.InvalidPaymentMethod
import com.team.pipeline.domain.InvalidStatus
import com.team.pipeline.domain.InvalidTimestamp
import com.team.pipeline.domain.InvalidTransactionCountry
import com.team.pipeline.domain.MerchantCategory
import com.team.pipeline.domain.NormalizedPaymentEvent
import com.team.pipeline.domain.PaymentChannel
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.domain.RawPaymentEvent
import com.team.pipeline.domain.ValidationError

import java.time.Instant
import scala.util.Try

trait EventNormalizer:
  def normalize(raw: RawPaymentEvent): Either[ValidationError, NormalizedPaymentEvent]

object EventNormalizer:
  val default: EventNormalizer = new EventNormalizer:
    override def normalize(
        raw: RawPaymentEvent
    ): Either[ValidationError, NormalizedPaymentEvent] =
      for
        timestamp <- normalizeTimestamp(raw.timestamp)
        currency <- normalizeCurrency(raw.currency)
        status <- normalizeStatus(raw.status)
        paymentMethod <- normalizePaymentMethod(raw.paymentMethod)
        transactionCountry <- normalizeCountry(raw.transactionCountry)
        merchantCategory <- normalizeMerchantCategory(raw.merchantCategory)
        channel <- normalizeChannel(raw.channel)
      yield NormalizedPaymentEvent(
        eventId = raw.eventId,
        timestamp = timestamp,
        customerId = raw.customerId,
        amount = raw.amount,
        currency = currency,
        status = status,
        paymentMethod = paymentMethod,
        transactionCountry = transactionCountry,
        merchantId = raw.merchantId.trim,
        merchantCategory = merchantCategory,
        channel = channel,
        deviceId = raw.deviceId.trim
      )

  def normalizeEnumKey(value: String): String =
    value.trim.toUpperCase

  def normalizeTimestamp(value: String): Either[ValidationError, Instant] =
    Try(Instant.parse(value)).toEither.left.map(_ => InvalidTimestamp(value))

  def normalizeCurrency(value: String): Either[ValidationError, Currency] =
    val normalized = normalizeEnumKey(value)
    Currency.fromCode(normalized).left.map(_ => InvalidCurrency(normalized))

  def normalizeStatus(value: String): Either[ValidationError, EventStatus] =
    val normalized = normalizeEnumKey(value)
    EventStatus.fromCode(normalized).left.map(_ => InvalidStatus(normalized))

  def normalizePaymentMethod(value: String): Either[ValidationError, PaymentMethod] =
    val normalized = normalizeEnumKey(value)
    PaymentMethod.fromCode(normalized).left.map(_ => InvalidPaymentMethod(normalized))

  def normalizeCountry(value: String): Either[ValidationError, String] =
    val normalized = normalizeEnumKey(value)
    Either.cond(
      normalized.length == 2 && normalized.forall(_.isLetter),
      normalized,
      InvalidTransactionCountry(normalized)
    )

  def normalizeMerchantCategory(value: String): Either[ValidationError, MerchantCategory] =
    val normalized = normalizeEnumKey(value)
    MerchantCategory.fromCode(normalized).left.map(_ => InvalidMerchantCategory(normalized))

  def normalizeChannel(value: String): Either[ValidationError, PaymentChannel] =
    val normalized = normalizeEnumKey(value)
    PaymentChannel.fromCode(normalized).left.map(_ => InvalidPaymentChannel(normalized))
