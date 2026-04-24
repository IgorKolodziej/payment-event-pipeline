package com.team.pipeline.infrastructure.postgres

import cats.effect.IO
import com.team.pipeline.domain.CustomerProfile
import com.team.pipeline.domain.PaymentMethod
import com.team.pipeline.ports.CustomerProfileLookup
import doobie.Read
import doobie.Transactor
import doobie.implicits.*

final class DoobieCustomerProfileLookup(transactor: Transactor[IO])
    extends CustomerProfileLookup:
  import DoobieCustomerProfileLookup.CustomerRow
  import DoobieCustomerProfileLookup.given

  override def find(customerId: Int): IO[Option[CustomerProfile]] =
    sql"""
      SELECT
        id,
        first_name,
        last_name,
        email,
        country,
        balance,
        daily_limit,
        has_blik,
        has_card,
        has_transfer,
        is_active,
        age,
        gender,
        last_login_country,
        fraud_before
      FROM customers
      WHERE id = $customerId
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
      BigDecimal,
      BigDecimal,
      Int,
      Int,
      Int,
      Boolean,
      Int,
      String,
      String,
      Int
  )

  private[postgres] final case class CustomerRow(
      id: Int,
      firstName: String,
      lastName: String,
      email: String,
      country: String,
      balance: BigDecimal,
      dailyLimit: BigDecimal,
      hasBlik: Int,
      hasCard: Int,
      hasTransfer: Int,
      isActive: Boolean,
      age: Int,
      gender: String,
      lastLoginCountry: String,
      fraudBefore: Int
  ):
    def toDomain: CustomerProfile =
      CustomerProfile(
        customerId = id,
        firstName = firstName,
        lastName = lastName,
        email = email,
        country = country,
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
        fraudBefore = fraudBefore == 1
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
