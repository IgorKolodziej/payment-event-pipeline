package com.team.pipeline

package object domain:
  opaque type EventId = Int

  object EventId:
    def apply(value: Int): EventId = value

    extension (id: EventId)
      def value: Int = id

  opaque type CustomerId = Int

  object CustomerId:
    def apply(value: Int): CustomerId = value

    extension (id: CustomerId)
      def value: Int = id
