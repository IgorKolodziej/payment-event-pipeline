package com.team.pipeline.application.enrichment

import com.team.pipeline.application.validation.EmailHasher
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.NormalizedPaymentEvent

object EventEnricher:
  def enrich(
      event: NormalizedPaymentEvent,
      customer: CustomerProfile,
      emailSalt: String
  ): EnrichedPaymentEvent =
    EnrichedPaymentEvent(
      event = event,
      customer = customer,
      hashedCustomerEmail = EmailHasher.sha256(emailSalt).hash(customer.email)
    )
