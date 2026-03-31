package ru.rdnn.api

import zio._
import ru.rdnn.DataBaseService
import ru.rdnn.dbrepositories.TransferRequest
import zio.http._
import zio.json._
import ru.rdnn.AppError
import ru.rdnn.AppError._
import ru.rdnn.configuration.Configuration
import ru.rdnn.dbrepositories.UserRepository

import javax.sql.DataSource

object MoneyTransferAPI {
  final case class AuthRequest(userId: String)
  final case class AuthResponse(token: String, tokenType: String = "Bearer")

  implicit val authRequestDecoder: JsonDecoder[AuthRequest] = DeriveJsonDecoder.gen[AuthRequest]
  implicit val authResponseEncoder: JsonEncoder[AuthResponse] = DeriveJsonEncoder.gen[AuthResponse]

  val api: Routes[DataSource with DataBaseService with UserRepository, Nothing] = Routes(
    Method.POST / "auth" / "token" -> handler { (req: Request) =>
      (
        for {
          body <- req.body.asString
          authRequest <- ZIO
            .fromEither(body.fromJson[AuthRequest])
            .mapError(JsonDecodingError.apply)
          userId <- ZIO
            .attempt(java.util.UUID.fromString(authRequest.userId))
            .mapError(_ => JsonDecodingError("userId must be valid UUID"))
          userRepository <- ZIO.service[UserRepository]
          maybeUser <- userRepository.findUserById(userId)
          _ <- ZIO.fail(AppError.AccountNotFound(authRequest.userId)).when(maybeUser.isEmpty)
          config <- Configuration.config.mapError(err => DbError(err))
          token <- JwtAuthAPI.generateToken(authRequest.userId, config.jwt.secret, config.jwt.ttlSeconds)
            .mapError(msg => JsonDecodingError(msg))
        } yield Response.json(AuthResponse(token = token).toJson)
      ).catchAll {
        case JsonDecodingError(details) =>
          ZIO.succeed(Response.badRequest(s"Invalid request: $details"))
        case AccountNotFound(acc) =>
          ZIO.succeed(Response.notFound(s"User not found: $acc"))
        case DbError(cause) =>
          ZIO.succeed(Response.internalServerError(s"Configuration/DB error: ${cause.getMessage}"))
        case other =>
          ZIO.succeed(Response.internalServerError(s"Unexpected error: ${other.getMessage}"))
      }
    },
    Method.POST / "transfer" / "account-number" -> handler { (req: Request) =>
      (
        for {
          authHeader <- ZIO
            .fromOption(req.header(Header.Authorization))
            .mapError(_ => JsonDecodingError("Missing Authorization header"))
          token <- JwtAuthAPI.extractBearerToken(authHeader.renderedValue).mapError(JsonDecodingError.apply)
          config <- Configuration.config.mapError(err => DbError(err))
          claims <- JwtAuthAPI.validateToken(token, config.jwt.secret).mapError(JsonDecodingError.apply)
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
          val message = s"Unauthorized or invalid request: $details"
          ZIO.succeed(Response.unauthorized(message))
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
