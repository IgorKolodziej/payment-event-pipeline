package com.team.pipeline.infrastructure.postgres

import com.team.pipeline.domain.PaymentMethod
import munit.FunSuite

import java.time.Instant
import java.time.LocalDateTime

class DoobieCustomerProfileLookupTest extends FunSuite:
  test("maps customer row flags to customer profile") {
    val row = DoobieCustomerProfileLookup.CustomerRow(
      id = 10,
      firstName = "Beata",
      lastName = "Krolak",
      email = "b.krolak@firma.pl",
      country = "PL",
      balance = BigDecimal("5500.00"),
      dailyLimit = BigDecimal("5000.00"),
      hasBlik = 1,
      hasCard = 0,
      hasTransfer = 1,
      isActive = true,
      age = 38,
      gender = "F",
      lastLoginCountry = "PL",
      fraudBefore = 0,
      createdAt = LocalDateTime.parse("2021-06-18T10:00:00")
    )

    val profile = row.toDomain

    assertEquals(profile.customerId, 10)
    assertEquals(profile.allowedPaymentMethods, Set(PaymentMethod.Blik, PaymentMethod.Transfer))
    assertEquals(profile.fraudBefore, false)
    assertEquals(profile.createdAt, Instant.parse("2021-06-18T10:00:00Z"))
  }
end DoobieCustomerProfileLookupTest
