package com.team.pipeline.application.parsing

import com.team.pipeline.domain.RawPaymentEvent
import io.circe.Decoder

object EventParser:
  given Decoder[RawPaymentEvent] =
    Decoder.forProduct8(
      "eventId",
      "timestamp",
      "customerId",
      "amount",
      "status",
      "has_blik",
      "has_card",
      "has_transfer"
    )(RawPaymentEvent.apply)
