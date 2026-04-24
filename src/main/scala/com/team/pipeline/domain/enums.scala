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
