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

enum AlertType:
  case InactiveCustomer
  case LimitExceeded
  case InvalidPaymentMethod
  case PreviouslyFlaggedCustomer
