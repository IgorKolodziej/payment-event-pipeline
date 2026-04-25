package com.team.pipeline.domain

enum EventStatus:
  case Success, Failed

enum PaymentMethod:
  case Blik, Card, Transfer

enum AlertType:
  case InactiveCustomer
  case LimitExceeded
  case InvalidPaymentMethod
  case PreviouslyFlaggedCustomer
  case CountryLoginMismatch
  case NewAccountNearLimit
  case SeniorNearLimitHighRiskMethod
  case Rolling24hLimitExceeded
  case VelocitySpike
  case NightBurstNearLimit
  case ZScoreAmountOutlier
  case IqrAmountOutlier
