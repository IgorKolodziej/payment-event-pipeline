package com.team.pipeline.domain

import cats.data.NonEmptyChain

import java.time.Instant

final case class RawPaymentEvent(
    eventId: EventId,
    timestamp: String,
    customerId: CustomerId,
    amount: BigDecimal,
    currency: String,
    status: String,
    paymentMethod: String,
    transactionCountry: String,
    merchantId: String,
    merchantCategory: String,
    channel: String,
    deviceId: String
)

final case class NormalizedPaymentEvent(
    eventId: EventId,
    timestamp: Instant,
    customerId: CustomerId,
    amount: BigDecimal,
    currency: Currency,
    status: EventStatus,
    paymentMethod: PaymentMethod,
    transactionCountry: String,
    merchantId: String,
    merchantCategory: MerchantCategory,
    channel: PaymentChannel,
    deviceId: String
)

final case class CustomerProfile(
    customerId: CustomerId,
    firstName: String,
    lastName: String,
    email: String,
    country: String,
    accountCurrency: Currency,
    balance: BigDecimal,
    dailyLimit: BigDecimal,
    allowedPaymentMethods: Set[PaymentMethod],
    isActive: Boolean,
    age: Int,
    gender: String,
    lastLoginCountry: String,
    fraudBefore: Boolean,
    createdAt: Instant
)

final case class EnrichedPaymentEvent(
    event: NormalizedPaymentEvent,
    customer: CustomerProfile,
    hashedCustomerEmail: String
)

final case class EligibilityAssessment(
    decision: EligibilityDecision,
    violations: List[EligibilityViolation]
)

final case class EligibilityViolation(
    violationType: EligibilityViolationType,
    eventId: EventId,
    customerId: CustomerId,
    message: String
)

final case class RiskAssessment(
    riskScore: Int,
    decision: RiskDecision,
    alerts: List[Alert]
)

final case class PaymentAssessment(
    eligibility: EligibilityAssessment,
    risk: Option[RiskAssessment],
    finalDecision: FinalDecision
)

final case class ProcessedEvent(
    eventId: EventId,
    customerId: CustomerId,
    timestamp: Instant,
    amount: BigDecimal,
    currency: Currency,
    status: EventStatus,
    paymentMethod: PaymentMethod,
    transactionCountry: String,
    merchantId: String,
    merchantCategory: MerchantCategory,
    channel: PaymentChannel,
    deviceId: String,
    customerCountry: String,
    customerAccountCurrency: Currency,
    hashedCustomerEmail: String,
    riskScore: Int,
    riskDecision: RiskDecision,
    finalDecision: FinalDecision
)

final case class Alert(
    alertType: AlertType,
    eventId: EventId,
    customerId: CustomerId,
    message: String,
    riskScore: Int
)

final case class RejectedEvent(
    sourcePosition: Long,
    eventId: Option[EventId],
    customerId: Option[CustomerId],
    reasons: NonEmptyChain[DataError]
)
