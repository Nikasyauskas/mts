package ru.rdnn

import zio._
import zio.config.typesafe.TypesafeConfigProvider
import ru.rdnn.configuration.Configuration
import ru.rdnn.api.{JwtAuthAPI, MoneyTransferAPI}
import ru.rdnn.dbrepositories.{AccountsRepository, BalanceHistoryRepository, TransactionsRepository, UserRepository}
import zio.http.Header
import zio.http.Middleware.{CorsConfig, cors}
import zio.http.Server

object Main extends ZIOAppDefault {

  /** Browser dev servers (Vite default 5173, etc.); tighten for production. */
  private val corsConfig: CorsConfig = CorsConfig(
    allowedOrigin = {
      case v @ Header.Origin.Value(scheme, host, port) =>
        val devPortOk = port.forall(p => p == 5173 || p == 3000 || p == 4173)
        val localHost = host == "localhost" || host == "127.0.0.1"
        if (scheme == "http" && localHost && devPortOk)
          Some(Header.AccessControlAllowOrigin.Specific(v))
        else None
      case _ => None
    },
  )

  override val bootstrap: ULayer[Unit] =
    Runtime.setConfigProvider(
      TypesafeConfigProvider
        .fromResourcePath()
    ) ++ Logger.liveCustomLogger

  override def run: ZIO[Any, Exception, Unit] = for {
    _ <- Configuration.config
    _ <- Server.serve((JwtAuthAPI.authRoutes ++ MoneyTransferAPI.api) @@ cors(corsConfig))
      .provide(
        Server.default,
        db.quillDS,
        AccountsRepository.live,
        BalanceHistoryRepository.live,
        TransactionsRepository.live,
        UserRepository.live,
        DataBaseService.live
      )
      .orDie
  } yield ()
}
