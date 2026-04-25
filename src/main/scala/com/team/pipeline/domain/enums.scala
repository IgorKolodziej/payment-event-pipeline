package com.team.pipeline.domain

enum Currency:
  case PLN, EUR, USD, GBP

enum EventStatus:
  case Success, Failed

enum PaymentMethod:
  case Blik, Card, Transfer

enum MerchantCategory:
  case Grocery, Electronics, Travel, Entertainment, Utilities, Other

enum PaymentChannel:
  case Mobile, Web, Pos

enum EligibilityDecision:
  case Eligible, Declined

enum EligibilityViolationType:
  case InactiveCustomer
  case InsufficientBalance
  case SingleTransactionLimitExceeded
  case DailyLimitExceeded
  case PaymentMethodNotAllowed

enum RiskDecision:
  case Approve, Review, Block

enum FinalDecision:
  case Accepted, Declined, Review, BlockedByRisk

enum AlertType:
  case PreviouslyFlaggedCustomer
  case CountryMismatch
  case NewAccountHighAmount
  case LateNightTransaction
  case VelocitySpike
  case FailedAttemptBurst
  case RepeatedLateNightActivity
  case NewDeviceHighRisk
  case AmountOutlier
  case SeniorMethodShiftAnomaly
