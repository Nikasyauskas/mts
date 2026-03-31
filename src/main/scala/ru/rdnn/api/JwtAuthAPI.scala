package ru.rdnn.api

import zio._
import zio.json._
import zio.http.Header
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

object JwtAuthAPI {
  final case class JwtClaims(
    userId: String,
    exp: Long,
    iat: Long
  )

  private implicit val claimsDecoder: JsonDecoder[JwtClaims] = DeriveJsonDecoder.gen[JwtClaims]
  private implicit val claimsEncoder: JsonEncoder[JwtClaims] = DeriveJsonEncoder.gen[JwtClaims]

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
}
