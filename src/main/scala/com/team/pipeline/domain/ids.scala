package com.team.pipeline.domain

opaque type EventId = Int

object EventId:
  def apply(value: Int): EventId = value

  def fromInt(value: Int): Either[InvalidEventId, EventId] =
    Either.cond(value > 0, value, InvalidEventId(value))

  def unsafe(value: Int): EventId =
    fromInt(value).fold(error => throw new IllegalArgumentException(error.toString), identity)

  extension (id: EventId)
    def value: Int = id

opaque type CustomerId = Int

object CustomerId:
  def apply(value: Int): CustomerId = value

  def fromInt(value: Int): Either[InvalidCustomerId, CustomerId] =
    Either.cond(value > 0, value, InvalidCustomerId(value))

  def unsafe(value: Int): CustomerId =
    fromInt(value).fold(error => throw new IllegalArgumentException(error.toString), identity)

  extension (id: CustomerId)
    def value: Int = id
