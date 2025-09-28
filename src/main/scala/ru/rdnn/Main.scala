package ru.rdnn

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import zio._
import zio.config.typesafe.TypesafeConfigProvider
import ru.rdnn.configuration.Configuration
import ru.rdnn.DataService


object Main extends ZIOAppDefault {

  override val bootstrap: ULayer[Unit] =
    Runtime.setConfigProvider(
        TypesafeConfigProvider
          .fromResourcePath()
      ) ++ Logger.liveDefaultLogger

  private def app = for {
    conf <- Configuration.config
    _ <- ZIO.logInfo(s"user name is: ${conf.database.username}")
    list <- DataService.listUserAccounts
    _ <- ZIO.logInfo(s"Accounts: ${list.mkString(", ")}")
  } yield ()

  override def run: ZIO[Any, Exception, Unit] = app
     .provide(
       DataRepository.live,
       DataService.live,
       Quill.Postgres.fromNamingStrategy(SnakeCase),
       Quill.DataSource.fromPrefix("database").mapError(_.asInstanceOf[Exception])
     ).orDie
}
