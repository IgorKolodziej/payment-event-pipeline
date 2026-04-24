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

enum RiskDecision:
  case Approve, Review, Block

enum AlertType:
  case InactiveCustomer
  case LimitExceeded
  case InvalidPaymentMethod
  case PreviouslyFlaggedCustomer
  case CountryMismatch
  case NewAccountHighAmount
  case LateNightTransaction
  case CumulativeLimitExceeded
  case VelocitySpike
  case FailedAttemptBurst
  case RepeatedLateNightActivity
  case NewDeviceHighRisk
  case AmountOutlier
