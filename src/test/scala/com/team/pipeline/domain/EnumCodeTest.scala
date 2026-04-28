package com.team.pipeline.domain

import munit.FunSuite

class EnumCodeTest extends FunSuite:
  test("input enums expose stable external codes") {
    assertEquals(Currency.PLN.code, "PLN")
    assertEquals(EventStatus.Success.code, "SUCCESS")
    assertEquals(PaymentMethod.Blik.code, "BLIK")
    assertEquals(MerchantCategory.Grocery.code, "GROCERY")
    assertEquals(PaymentChannel.Mobile.code, "MOBILE")
  }

  test("input enums parse stable codes and legacy case names") {
    assertEquals(EventStatus.fromCode("SUCCESS"), Right(EventStatus.Success))
    assertEquals(EventStatus.fromCode("Success"), Right(EventStatus.Success))
    assertEquals(PaymentMethod.fromCode("BLIK"), Right(PaymentMethod.Blik))
    assertEquals(PaymentMethod.fromCode("Blik"), Right(PaymentMethod.Blik))
    assertEquals(MerchantCategory.fromCode("GROCERY"), Right(MerchantCategory.Grocery))
    assertEquals(PaymentChannel.fromCode("Mobile"), Right(PaymentChannel.Mobile))
  }

  test("reporting enums keep existing public codes") {
    assertEquals(FinalDecision.BlockedByRisk.code, "BlockedByRisk")
    assertEquals(RiskDecision.NotEvaluated.code, "NotEvaluated")
    assertEquals(EligibilityViolationType.CurrencyMismatch.code, "CurrencyMismatch")
    assertEquals(AlertType.VelocitySpike.code, "VelocitySpike")
  }
