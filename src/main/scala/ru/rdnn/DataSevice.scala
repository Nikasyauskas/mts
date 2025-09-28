package ru.rdnn

import io.getquill.context.ZioJdbc.QIO
import zio.{ULayer, URIO, ZIO, ZLayer}

import javax.sql.DataSource

trait DataService {
  /* функция - должна возвращать список UserAccount*/
  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]]
  /* функция - должна производить транзакцию с одного счета на другой */
//  def provideTransaction(userAccountFrom: UserAccount, userAccountTo: UserAccount): ZIO[DataSource, Throwable, Unit]
}

class DataServiceImpl(repository: DataRepository) extends DataService {
  
  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]] = 
    repository.listUserAccounts
  
//  def provideTransaction(userAccountFrom: UserAccount, userAccountTo: UserAccount): ZIO[DataSource, Throwable, Unit] =
//    for {
//      ds <- ZIO.service[DataSource]
//      _ <- ZIO.logInfo(s"Transferring from ${userAccountFrom.account_number} to ${userAccountTo.account_number}")
//      // Здесь должна быть логика транзакции
//      // Пока что просто логируем операцию
//      _ <- ZIO.succeed(())
//    } yield ()
}


object DataService {
  def listUserAccounts: ZIO[DataSource with DataService, Throwable, List[UserAccount]] =
    ZIO.service[DataService].flatMap(_.listUserAccounts)
    
  val live: ZLayer[DataRepository, Nothing, DataService] = 
    ZLayer.fromFunction(new DataServiceImpl(_))
}
