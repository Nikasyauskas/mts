package ru.rdnn

sealed trait AppError extends Throwable {
  def message: String
  override def getMessage: String = message
}

object AppError {

  final case class AccountNotFound(accountNumber: String) extends AppError {
    override val message: String = s"Account not found: $accountNumber"
  }

  final case class BalanceHistoryNotFound(accountNumber: String) extends AppError {
    override val message: String = s"Balance history not found for account: $accountNumber"
  }

  final case class InsufficientBalance(required: Float, available: Float) extends AppError {
    override val message: String =
      s"Insufficient balance. Required: $required, Available: $available"
  }

  final case class NonPositiveAmount(amount: Float) extends AppError {
    override val message: String = s"Transaction amount must be positive. Got: $amount"
  }

  final case class JsonDecodingError(details: String) extends AppError {
    override val message: String = s"Invalid JSON: $details"
  }

  final case class DbError(cause: Throwable) extends AppError {
    override val message: String = s"Database error: ${cause.getMessage}"
    override def getCause: Throwable = cause
  }
}

