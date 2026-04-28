package com.team.pipeline.infrastructure.postgres

import cats.effect.IO
import com.team.pipeline.domain.Currency
import com.team.pipeline.domain.CustomerId
import com.team.pipeline.domain.CustomerId.*
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.ports.CustomerProfileLookup
import doobie.Read
import doobie.Transactor
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.JavaLocalDateTimeMeta

import java.time.LocalDateTime
import java.time.ZoneOffset

final class DoobieCustomerProfileLookup(transactor: Transactor[IO])
    extends CustomerProfileLookup:
  import DoobieCustomerProfileLookup.CustomerRow
  import DoobieCustomerProfileLookup.given

  override def find(customerId: CustomerId): IO[Option[CustomerProfile]] =
    val rawCustomerId = customerId.value

    sql"""
      SELECT
        id,
        first_name,
        last_name,
        email,
        country,
        account_currency,
        balance,
        daily_limit,
        has_blik,
        has_card,
        has_transfer,
        is_active,
        age,
        gender,
        last_login_country,
        fraud_before,
        created_at
      FROM customers
      WHERE id = $rawCustomerId
    """
      .query[CustomerRow]
      .option
      .transact(transactor)
      .map(_.map(_.toDomain))

object DoobieCustomerProfileLookup:
  private type CustomerTuple = (
      Int,
      String,
      String,
      String,
      String,
      String,
      BigDecimal,
      BigDecimal,
      Int,
      Int,
      Int,
      Boolean,
      Int,
      String,
      String,
      Int,
      LocalDateTime
  )

  private[postgres] final case class CustomerRow(
      id: Int,
      firstName: String,
      lastName: String,
      email: String,
      country: String,
      accountCurrency: String,
      balance: BigDecimal,
      dailyLimit: BigDecimal,
      hasBlik: Int,
      hasCard: Int,
      hasTransfer: Int,
      isActive: Boolean,
      age: Int,
      gender: String,
      lastLoginCountry: String,
      fraudBefore: Int,
      createdAt: LocalDateTime
  ):
    def toDomain: CustomerProfile =
      CustomerProfile(
        customerId = CustomerId(id),
        firstName = firstName,
        lastName = lastName,
        email = email,
        country = country,
        accountCurrency = Currency.valueOf(accountCurrency.trim),
        balance = balance,
        dailyLimit = dailyLimit,
        allowedPaymentMethods = paymentMethods(
          hasBlik = hasBlik,
          hasCard = hasCard,
          hasTransfer = hasTransfer
        ),
        isActive = isActive,
        age = age,
        gender = gender,
        lastLoginCountry = lastLoginCountry,
        fraudBefore = fraudBefore == 1,
        createdAt = createdAt.toInstant(ZoneOffset.UTC)
      )

  private[postgres] given Read[CustomerRow] =
    Read[CustomerTuple].map(CustomerRow.apply.tupled)

  private def paymentMethods(
      hasBlik: Int,
      hasCard: Int,
      hasTransfer: Int
  ): Set[PaymentMethod] =
    Set(
      Option.when(hasBlik == 1)(PaymentMethod.Blik),
      Option.when(hasCard == 1)(PaymentMethod.Card),
      Option.when(hasTransfer == 1)(PaymentMethod.Transfer)
    ).flatten
