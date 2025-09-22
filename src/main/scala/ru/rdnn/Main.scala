package ru.rdnn

import zio._
import ru.rdnn.AppConf
import zio.config.typesafe.TypesafeConfigProvider

object Main extends ZIOAppDefault {

  override val bootstrap =
    Runtime.setConfigProvider(
        TypesafeConfigProvider
          .fromResourcePath()
      )

  private def app: ZIO[Any, Config.Error, Unit] = for {
    conf <- ZIO.config[Conf](AppConf.conf)
    _ <- ZIO.logInfo(
      s"""
        |host: ${conf.host}
        |port: ${conf.port}
        |""".stripMargin)
  } yield ()

  override def run: ZIO[Any, Config.Error, Unit] = app
}
