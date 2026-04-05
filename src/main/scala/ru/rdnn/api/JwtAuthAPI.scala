package ru.rdnn.api

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import ru.rdnn.AppError.{DbError, EmailAlreadyRegistered, JsonDecodingError}
import ru.rdnn.configuration.Configuration
import ru.rdnn.dbrepositories.{AccountsRepository, BalanceHistoryRepository, UserRepository}
import ru.rdnn.{PasswordHash, UserRegistration}
import zio._
import zio.http._
import zio.json._

import javax.sql.DataSource

object JwtAuthAPI {
  final case class JwtClaims(
    userId: String,
    exp: Long,
    iat: Long
  )

  final case class LoginRequest(email: String, password: String)
  final case class RegisterRequest(email: String, password: String, userName: String, phone: String = "")
  final case class RegisterResponse(userId: String, accountNumber: String)
  final case class AuthResponse(token: String, tokenType: String = "Bearer", accountNumbers: List[String] = Nil)

  private implicit val claimsDecoder: JsonDecoder[JwtClaims] = DeriveJsonDecoder.gen[JwtClaims]
  private implicit val claimsEncoder: JsonEncoder[JwtClaims] = DeriveJsonEncoder.gen[JwtClaims]
  implicit val loginRequestDecoder: JsonDecoder[LoginRequest] = DeriveJsonDecoder.gen[LoginRequest]
  implicit val registerRequestDecoder: JsonDecoder[RegisterRequest] = DeriveJsonDecoder.gen[RegisterRequest]
  implicit val registerResponseEncoder: JsonEncoder[RegisterResponse] = DeriveJsonEncoder.gen[RegisterResponse]
  implicit val authResponseEncoder: JsonEncoder[AuthResponse] = DeriveJsonEncoder.gen[AuthResponse]
  implicit val authResponseDecoder: JsonDecoder[AuthResponse] = DeriveJsonDecoder.gen[AuthResponse]

  def generateToken(userId: String, secret: String, ttlSeconds: Long): IO[String, String] =
    for {
      now <- Clock.instant.map(_.getEpochSecond)
      claims = JwtClaims(
        userId = userId,
        exp = now + ttlSeconds,
        iat = now
      )
      token <- ZIO
        .attempt {
          val claim = JwtClaim(content = claims.toJson).issuedAt(now).expiresAt(now + ttlSeconds)
          Jwt.encode(claim, secret, JwtAlgorithm.HS256)
        }
        .mapError(_.getMessage)
    } yield token

  def validateToken(token: String, secret: String): IO[String, JwtClaims] =
    for {
      decoded <- ZIO
        .fromTry(Jwt.decode(token, secret, Seq(JwtAlgorithm.HS256)))
        .mapError(_.getMessage)
      claims <- ZIO
        .fromEither(decoded.content.fromJson[JwtClaims])
        .mapError(err => s"Invalid JWT payload: $err")
      now <- Clock.instant.map(_.getEpochSecond)
      _ <- ZIO.fail("JWT is expired").when(claims.exp <= now)
    } yield claims

  def extractBearerToken(authorizationHeader: String): IO[String, String] =
    ZIO
      .fromEither(Header.Authorization.parse(authorizationHeader))
      .flatMap {
        case Header.Authorization.Bearer(token) if token.value.nonEmpty => ZIO.succeed(token.value.asString)
        case _ => ZIO.fail("Authorization header must be in format: Bearer <token>")
      }
      .mapError(_ => "Authorization header must be in format: Bearer <token>")

  def jwtBearerAspect(realm: String): HandlerAspect[Any, JwtClaims] =
    HandlerAspect.interceptIncomingHandler {
      handler { (request: Request) =>
        val unauthorized =
          Response
            .unauthorized("Unauthorized")
            .addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm)))
        def invalid(msg: String) =
          Response.unauthorized(msg).addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm)))

        for {
          conf <- Configuration.config.mapError(_ => Response.internalServerError("Configuration error"))
          hdr  <- ZIO.fromOption(request.header(Header.Authorization)).orElseFail(unauthorized)
          token <- JwtAuthAPI
            .extractBearerToken(hdr.renderedValue)
            .mapError(invalid)
          claims <- JwtAuthAPI
            .validateToken(token, conf.jwt.secret)
            .mapError(invalid)
        } yield (request, claims)
      }
    }

  val authRoutes: Routes[
    DataSource with UserRepository with AccountsRepository with BalanceHistoryRepository,
    Nothing
  ] = Routes(
    Method.POST / "auth" / "register" -> handler { (req: Request) =>
      (
        for {
          body <- req.body.asString
          reg  <- ZIO.fromEither(body.fromJson[RegisterRequest]).mapError(JsonDecodingError.apply)
          _    <- ZIO.fail(JsonDecodingError("email must be non-empty")).when(reg.email.trim.isEmpty)
          _    <- ZIO.fail(JsonDecodingError("password must be at least 8 characters")).when(reg.password.length < 8)
          _    <- ZIO.fail(JsonDecodingError("userName must be non-empty")).when(reg.userName.trim.isEmpty)
          pair <- UserRegistration.register(
            email = reg.email.trim,
            passwordPlain = reg.password,
            userName = reg.userName.trim,
            phone = reg.phone.trim
          )
          resp = RegisterResponse(userId = pair._1.toString, accountNumber = pair._2)
        } yield Response.json(resp.toJson)
      ).catchAll {
        case JsonDecodingError(d) =>
          ZIO.succeed(Response.badRequest(s"Invalid request: $d"))
        case EmailAlreadyRegistered(e) =>
          ZIO.succeed(Response.text(s"Email already registered: $e").status(Status.Conflict))
        case DbError(cause) =>
          ZIO.succeed(Response.internalServerError(s"Database error: ${cause.getMessage}"))
        case other =>
          ZIO.succeed(Response.internalServerError(s"Unexpected error: ${other.getMessage}"))
      }
    },
    Method.POST / "auth" / "token" -> handler { (req: Request) =>
      (
        for {
          body  <- req.body.asString
          login <- ZIO.fromEither(body.fromJson[LoginRequest]).mapError(JsonDecodingError.apply)
          _     <- ZIO.fail(JsonDecodingError("Invalid email or password")).when(login.email.trim.isEmpty)
          userRepo <- ZIO.service[UserRepository]
          userOpt  <- userRepo.findUserByEmail(login.email.trim)
          user     <- ZIO.fromOption(userOpt).orElseFail(JsonDecodingError("Invalid email or password"))
          ok       <- PasswordHash.verify(login.password, user.password_hash).mapError(_ => JsonDecodingError("Invalid email or password"))
          _        <- ZIO.fail(JsonDecodingError("Invalid email or password")).when(!ok)
          conf         <- Configuration.config.mapError(e => DbError(new RuntimeException(e.toString)))
          accountsRepo <- ZIO.service[AccountsRepository]
          accountNums <- accountsRepo.listAccountNumbersByUserId(user.id).mapError {
            case d: DbError => d
            case e          => DbError(e)
          }
          token <- JwtAuthAPI
            .generateToken(user.id.toString, conf.jwt.secret, conf.jwt.ttlSeconds)
            .mapError(msg => JsonDecodingError(msg))
        } yield Response.json(AuthResponse(token = token, accountNumbers = accountNums).toJson)
      ).catchAll {
        case JsonDecodingError(d) =>
          ZIO.succeed(
            Response
              .unauthorized(s"Unauthorized: $d")
              .addHeaders(Headers(Header.WWWAuthenticate.Bearer("login")))
          )
        case DbError(cause) =>
          ZIO.succeed(Response.internalServerError(s"Database error: ${cause.getMessage}"))
        case other =>
          ZIO.succeed(Response.internalServerError(s"Unexpected error: ${other.getMessage}"))
      }
    }
  )

}
