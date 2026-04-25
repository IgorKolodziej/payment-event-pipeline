package com.team.pipeline.domain

import java.time.Instant

final case class RawPaymentEvent(
    eventId: Int,
    timestamp: String,
    customerId: Int,
    amount: BigDecimal,
    status: Int,
    hasBlik: Int,
    hasCard: Int,
    hasTransfer: Int
)

final case class NormalizedPaymentEvent(
    eventId: Int,
    timestamp: Instant,
    customerId: Int,
    amount: BigDecimal,
    status: EventStatus,
    paymentMethods: Set[PaymentMethod]
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
    createdAt: Option[Instant] = None
)

final case class EnrichedPaymentEvent(
    event: NormalizedPaymentEvent,
    customer: CustomerProfile,
    hashedCustomerEmail: String
)

final case class RiskAssessment(
    riskScore: Int,
    alerts: List[Alert]
)

final case class ProcessedEvent(
    eventId: Int,
    customerId: Int,
    timestamp: Instant,
    amount: BigDecimal,
    status: EventStatus,
    paymentMethods: Set[PaymentMethod],
    customerCountry: String,
    hashedCustomerEmail: String,
    riskScore: Int
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

final case class RunSummary(
    totalRead: Int,
    totalProcessed: Int,
    totalRejected: Int,
    totalAlerts: Int,
    errorCounts: Map[String, Int],
    alertCounts: Map[String, Int]
)

final case class DashboardSnapshot(
    totalProcessed: Int,
    totalRejected: Int,
    totalAlerts: Int,
    alertCounts: Map[String, Int],
    topCountries: Map[String, Int]
)
