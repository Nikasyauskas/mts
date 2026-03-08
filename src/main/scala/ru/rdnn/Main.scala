package ru.rdnn

import zio._
import zio.config.typesafe.TypesafeConfigProvider
import ru.rdnn.configuration.Configuration
import ru.rdnn.api.MoneyTransferAPI
import ru.rdnn.dbrepositories.{AccountsRepository, BalanceHistoryRepository, TransactionsRepository, UserRepository}
import zio.http.Server

object Main extends ZIOAppDefault {

  override val bootstrap: ULayer[Unit] =
    Runtime.setConfigProvider(
      TypesafeConfigProvider
        .fromResourcePath()
    ) ++ Logger.liveCustomLogger

  override def run: ZIO[Any, Exception, Unit] = for {
    _ <- Configuration.config
    _ <- Server.serve(MoneyTransferAPI.api)
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
