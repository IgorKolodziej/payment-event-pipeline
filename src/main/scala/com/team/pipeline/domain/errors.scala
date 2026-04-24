package com.team.pipeline.domain

sealed trait DataError

sealed trait ParseError extends DataError
sealed trait ValidationError extends DataError
sealed trait EnrichmentError extends DataError

final case class InvalidJson(message: String) extends ParseError
final case class MissingField(field: String) extends ParseError

final case class InvalidTimestamp(value: String) extends ValidationError
final case class InvalidAmount(value: BigDecimal) extends ValidationError
final case class InvalidStatus(value: Int) extends ValidationError
final case class InvalidPaymentMethodFlags(
    hasBlik: Int,
    hasCard: Int,
    hasTransfer: Int
) extends ValidationError
case object NoPaymentMethodSelected extends ValidationError

final case class CustomerNotFound(customerId: Int) extends EnrichmentError
