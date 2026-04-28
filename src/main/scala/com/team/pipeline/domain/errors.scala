package com.team.pipeline.domain

sealed trait DataError

sealed trait ParseError extends DataError
sealed trait ValidationError extends DataError
sealed trait EnrichmentError extends DataError

final case class InvalidJson(message: String) extends ParseError
final case class MissingField(field: String) extends ParseError

final case class InvalidTimestamp(value: String) extends ValidationError
final case class InvalidAmount(value: BigDecimal) extends ValidationError
final case class InvalidCurrency(value: String) extends ValidationError
final case class InvalidStatus(value: String) extends ValidationError
final case class InvalidPaymentMethod(value: String) extends ValidationError
final case class InvalidTransactionCountry(value: String) extends ValidationError
final case class InvalidMerchantCategory(value: String) extends ValidationError
final case class InvalidPaymentChannel(value: String) extends ValidationError

final case class CustomerNotFound(customerId: CustomerId) extends EnrichmentError
