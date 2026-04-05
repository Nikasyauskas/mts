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
          claims <- ZIO.service[JwtAuthAPI.JwtClaims]
          body <- req.body.asString
          transferRequest <- ZIO
            .fromEither(body.fromJson[TransferRequest])
            .mapError(JsonDecodingError.apply)
          _ <- ZIO
            .fail(JsonDecodingError("Token user does not match transfer userId"))
            .when(claims.userId != transferRequest.userId)
          _ <- DataBaseService.provideTransaction(transferRequest)
        } yield Response.json("""Transfer completed successfully""")
      ).catchAll {
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
            if (details.contains("Token user does not match"))
              Response.unauthorized(s"Unauthorized or invalid request: $details")
            else
              Response.badRequest(s"Invalid request: $details")
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
    } @@ JwtAuthAPI.jwtBearerAspect("transfers")
  )
}
