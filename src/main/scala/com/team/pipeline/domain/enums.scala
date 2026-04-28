package com.team.pipeline.domain

private def parseByCode[A](
    values: Array[A],
    code: String,
    typeName: String
)(codeOf: A => String): Either[String, A] =
  val normalized = code.trim
  values
    .find(value => codeOf(value).equalsIgnoreCase(normalized))
    .toRight(s"Unsupported $typeName code: $normalized")

enum Currency(val code: String):
  case PLN extends Currency("PLN")
  case EUR extends Currency("EUR")
  case USD extends Currency("USD")
  case GBP extends Currency("GBP")

object Currency:
  def fromCode(code: String): Either[String, Currency] =
    parseByCode(values, code, "currency")(_.code)

enum EventStatus(val code: String):
  case Success extends EventStatus("SUCCESS")
  case Failed extends EventStatus("FAILED")

object EventStatus:
  def fromCode(code: String): Either[String, EventStatus] =
    parseByCode(values, code, "event status")(_.code)

enum PaymentMethod(val code: String):
  case Blik extends PaymentMethod("BLIK")
  case Card extends PaymentMethod("CARD")
  case Transfer extends PaymentMethod("TRANSFER")

object PaymentMethod:
  def fromCode(code: String): Either[String, PaymentMethod] =
    parseByCode(values, code, "payment method")(_.code)

enum MerchantCategory(val code: String):
  case Grocery extends MerchantCategory("GROCERY")
  case Electronics extends MerchantCategory("ELECTRONICS")
  case Travel extends MerchantCategory("TRAVEL")
  case Entertainment extends MerchantCategory("ENTERTAINMENT")
  case Utilities extends MerchantCategory("UTILITIES")
  case Other extends MerchantCategory("OTHER")

object MerchantCategory:
  def fromCode(code: String): Either[String, MerchantCategory] =
    parseByCode(values, code, "merchant category")(_.code)

enum PaymentChannel(val code: String):
  case Mobile extends PaymentChannel("MOBILE")
  case Web extends PaymentChannel("WEB")
  case Pos extends PaymentChannel("POS")

object PaymentChannel:
  def fromCode(code: String): Either[String, PaymentChannel] =
    parseByCode(values, code, "payment channel")(_.code)

enum EligibilityDecision(val code: String):
  case Eligible extends EligibilityDecision("Eligible")
  case Declined extends EligibilityDecision("Declined")

object EligibilityDecision:
  def fromCode(code: String): Either[String, EligibilityDecision] =
    parseByCode(values, code, "eligibility decision")(_.code)

enum EligibilityViolationType(val code: String):
  case InactiveCustomer extends EligibilityViolationType("InactiveCustomer")
  case CurrencyMismatch extends EligibilityViolationType("CurrencyMismatch")
  case InsufficientBalance extends EligibilityViolationType("InsufficientBalance")
  case SingleTransactionLimitExceeded
      extends EligibilityViolationType("SingleTransactionLimitExceeded")
  case DailyLimitExceeded extends EligibilityViolationType("DailyLimitExceeded")
  case PaymentMethodNotAllowed extends EligibilityViolationType("PaymentMethodNotAllowed")

object EligibilityViolationType:
  def fromCode(code: String): Either[String, EligibilityViolationType] =
    parseByCode(values, code, "eligibility violation type")(_.code)

enum RiskDecision(val code: String):
  case Approve extends RiskDecision("Approve")
  case Review extends RiskDecision("Review")
  case Block extends RiskDecision("Block")
  case NotEvaluated extends RiskDecision("NotEvaluated")

object RiskDecision:
  def fromCode(code: String): Either[String, RiskDecision] =
    parseByCode(values, code, "risk decision")(_.code)

enum FinalDecision(val code: String):
  case Accepted extends FinalDecision("Accepted")
  case Declined extends FinalDecision("Declined")
  case Review extends FinalDecision("Review")
  case BlockedByRisk extends FinalDecision("BlockedByRisk")

object FinalDecision:
  def fromCode(code: String): Either[String, FinalDecision] =
    parseByCode(values, code, "final decision")(_.code)

enum AlertType(val code: String):
  case PreviouslyFlaggedCustomer extends AlertType("PreviouslyFlaggedCustomer")
  case CountryMismatch extends AlertType("CountryMismatch")
  case NewAccountHighAmount extends AlertType("NewAccountHighAmount")
  case LateNightTransaction extends AlertType("LateNightTransaction")
  case VelocitySpike extends AlertType("VelocitySpike")
  case FailedAttemptBurst extends AlertType("FailedAttemptBurst")
  case RepeatedLateNightActivity extends AlertType("RepeatedLateNightActivity")
  case NewDeviceHighRisk extends AlertType("NewDeviceHighRisk")
  case AmountOutlier extends AlertType("AmountOutlier")
  case SeniorMethodShiftAnomaly extends AlertType("SeniorMethodShiftAnomaly")

object AlertType:
  def fromCode(code: String): Either[String, AlertType] =
    parseByCode(values, code, "alert type")(_.code)
