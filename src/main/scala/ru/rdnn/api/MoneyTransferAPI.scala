package ru.rdnn.api

import zio._
import ru.rdnn.DataBaseService
import ru.rdnn.dbrepositories.TransferRequest
import zio.http._
import zio.json._
import ru.rdnn.AppError
import ru.rdnn.AppError._

import javax.sql.DataSource

object MoneyTransferAPI {

  val api: Routes[DataSource with DataBaseService, Nothing] = Routes(
    Method.POST / "transfer" / "account-number" -> handler { (req: Request) =>
      (
        for {
          body <- req.body.asString
          transferRequest <- ZIO
            .fromEither(body.fromJson[TransferRequest])
            .mapError(err => JsonDecodingError(err): AppError)
          _ <- DataBaseService.provideTransaction(transferRequest)
        } yield Response.json(s"""Transfer completed successfully\n""")
      )
        .catchAll {
          case AccountNotFound(acc) =>
            ZIO.succeed(Response.notFound(s"Account not found: $acc"))
          case BalanceHistoryNotFound(acc) =>
            ZIO.succeed(Response.notFound(s"Balance history not found for account: $acc"))
          case InsufficientBalance(required, available) =>
            ZIO.succeed(
              Response.badRequest(s"Insufficient balance. Required: $required, Available: $available")
            )
          case NonPositiveAmount(amount) =>
            ZIO.succeed(
              Response.badRequest(s"Transaction amount must be positive. Got: $amount")
            )
          case JsonDecodingError(details) =>
            ZIO.succeed(
              Response.badRequest(s"Invalid JSON: $details")
            )
          case DbError(cause) =>
            ZIO.succeed(
              Response.internalServerError(s"Database error: ${cause.getMessage}")
            )
          case other =>
            ZIO.succeed(
              Response.internalServerError(s"Unexpected error: ${other.getMessage}")
            )
        }
    }
  )
}
