package com.team.pipeline.application.enrichment

import com.team.pipeline.application.validation.EmailHasher
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.CustomerNotFound
import com.team.pipeline.domain.EnrichedPaymentEvent
import com.team.pipeline.domain.EnrichmentError
import com.team.pipeline.domain.NormalizedPaymentEvent

object EventEnricher:
  def enrich(
      event: NormalizedPaymentEvent,
      customer: CustomerProfile,
      emailSalt: String
  ): EnrichedPaymentEvent =
    enrich(event, customer, EmailHasher.sha256(emailSalt))

  def enrich(
      event: NormalizedPaymentEvent,
      customer: CustomerProfile,
      emailHasher: EmailHasher
  ): EnrichedPaymentEvent =
    EnrichedPaymentEvent(
      event = event,
      customer = customer,
      hashedCustomerEmail = emailHasher.hash(customer.email)
    )

  def enrichOption(
      event: NormalizedPaymentEvent,
      customer: Option[CustomerProfile],
      emailSalt: String
  ): Either[EnrichmentError, EnrichedPaymentEvent] =
    enrichOption(event, customer, EmailHasher.sha256(emailSalt))

  def enrichOption(
      event: NormalizedPaymentEvent,
      customer: Option[CustomerProfile],
      emailHasher: EmailHasher
  ): Either[EnrichmentError, EnrichedPaymentEvent] =
    customer match
      case Some(profile) => Right(enrich(event, profile, emailHasher))
      case None          => Left(CustomerNotFound(event.customerId))
