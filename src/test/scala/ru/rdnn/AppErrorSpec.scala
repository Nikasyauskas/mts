package ru.rdnn

import ru.rdnn.AppError._
import zio.test._

object AppErrorSpec extends ZIOSpecDefault {

  def spec = suite("AppError")(
    test("AccountNotFound message") {
      val e = AccountNotFound("123")
      assertTrue(e.message == "Account not found: 123", e.getMessage == e.message)
    },
    test("BalanceHistoryNotFound message") {
      val e = BalanceHistoryNotFound("456")
      assertTrue(e.message == "Balance history not found for account: 456")
    },
    test("InsufficientBalance message") {
      val e = InsufficientBalance(100f, 50f)
      assertTrue(e.message == "Insufficient balance. Required: 100.0, Available: 50.0")
    },
    test("NonPositiveAmount message") {
      val e = NonPositiveAmount(-10f)
      assertTrue(e.message == "Transaction amount must be positive. Got: -10.0")
    },
    test("JsonDecodingError message") {
      val e = JsonDecodingError("unexpected token")
      assertTrue(e.message == "Invalid JSON: unexpected token")
    },
    test("EmailAlreadyRegistered message") {
      val e = EmailAlreadyRegistered("a@b.c")
      assertTrue(e.message == "Email already registered: a@b.c")
    },
    test("DbError message and cause") {
      val cause = new RuntimeException("connection refused")
      val e = DbError(cause)
      assertTrue(e.message.contains("connection refused"), e.getCause == cause)
    }
  )
}
