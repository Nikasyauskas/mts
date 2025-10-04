package ru.rdnn

import zio._
import zio.config.typesafe.TypesafeConfigProvider
import ru.rdnn.configuration.Configuration
import ru.rdnn.api.MoneyTransferAPI
import zio.http.Server

object Main extends ZIOAppDefault {

  override val bootstrap: ULayer[Unit] =
    Runtime.setConfigProvider(
      TypesafeConfigProvider
        .fromResourcePath()
    ) ++ Logger.liveCustomLogger

  override def run: ZIO[Any, Exception, Unit] = for {
    conf <- Configuration.config
    _ <- ZIO.logInfo(s"test configuration ${conf.server.host}:${conf.server.port}")
    _ <- Server.serve(MoneyTransferAPI.api)
      .provide(
        Server.default,
        db.quillDS,
        DataRepository.live,
        DataService.live
      )
      .orDie
  } yield ()
}
