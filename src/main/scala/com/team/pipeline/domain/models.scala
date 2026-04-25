package com.team.pipeline.domain

import java.time.Instant

final case class RawPaymentEvent(
    eventId: Int,
    timestamp: String,
    customerId: Int,
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
    eventId: Int,
    timestamp: Instant,
    customerId: Int,
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
    customerId: Int,
    firstName: String,
    lastName: String,
    email: String,
    country: String,
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
    eventId: Int,
    customerId: Int,
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
    eventId: Int,
    customerId: Int,
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
    hashedCustomerEmail: String,
    riskScore: Int,
    riskDecision: RiskDecision,
    finalDecision: FinalDecision
)

final case class Alert(
    alertType: AlertType,
    eventId: Int,
    customerId: Int,
    message: String,
    riskScore: Int
)

final case class RejectedEvent(
    lineNumber: Long,
    eventId: Option[Int],
    customerId: Option[Int],
    reason: DataError
)
