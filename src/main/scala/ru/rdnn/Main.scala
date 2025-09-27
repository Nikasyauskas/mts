package ru.rdnn

import zio._
import zio.config.typesafe.TypesafeConfigProvider

object Main extends ZIOAppDefault {

  override val bootstrap: ULayer[Unit] =
    Runtime.setConfigProvider(
        TypesafeConfigProvider
          .fromResourcePath()
      ) ++ Logger.liveDefaultLogger

  private def app: ZIO[Any, Config.Error, Unit] = for {
    conf <- ZIO.config[Conf](AppConf.conf)
    _ <- ZIO.logInfo(s"host: ${conf.host}; port: ${conf.port}")
  } yield ()

  override def run: ZIO[Any, Config.Error, Unit] = app
}
