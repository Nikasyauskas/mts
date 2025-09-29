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
    ) ++ Logger.liveCustomLogger

  private def app = for {
    conf <- Configuration.config
    _    <- ZIO.logInfo(s"user name is: ${conf.database.username}")
    list <- DataService.listUserAccounts
    _    <- ZIO.logInfo(s"Accounts: ${list.mkString("\n", "\n", "")}")
    _ <- DataService.updateUserAccount(
      UserAccount(java.util.UUID.fromString("bf7e2e36-350b-4ea7-ae7d-ff4ce38d3476"), "8901201003", 500.00)
    )
    updatedList <- DataService.listUserAccounts
    _           <- ZIO.logInfo(s"Updated Accounts: ${updatedList.mkString("\n", "\n", "")}")
  } yield ()

  override def run: ZIO[Any, Exception, Unit] = app
    .provide(
      Quill.DataSource.fromPrefix("database"),
      DataRepository.live,
      DataService.live
    )
    .orDie
}
