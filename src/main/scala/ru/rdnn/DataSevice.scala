package ru.rdnn

import io.getquill.context.ZioJdbc.QIO
import zio.{ULayer, URIO, ZIO, ZLayer}

import javax.sql.DataSource

trait DataService {
  /* функция - должна возвращать список UserAccount*/
  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]]
  /* функция - должна обновлять UserAccount */
  def updateUserAccount(userAccount: UserAccount): ZIO[DataSource, Throwable, Unit]
  /* функция - должна производить транзакцию с одного счета на другой */
  def provideTransaction(fromAccountId: java.util.UUID, toAccountId: java.util.UUID, amount: Double): ZIO[DataSource, Throwable, Unit]
}

class DataServiceImpl(repository: DataRepository) extends DataService {
  
  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]] = {
    repository.listUserAccounts
  }

  def updateUserAccount(userAccount: UserAccount): ZIO[DataSource, Throwable, Unit] = {
    repository.updateUserAccount(userAccount)
  }

  def provideTransaction(fromAccountId: java.util.UUID, toAccountId: java.util.UUID, amount: Double): ZIO[DataSource, Throwable, Unit] = {
    for {
      // Проверяем существование счетов
      fromAccountOpt <- repository.getUserAccountById(fromAccountId)
      toAccountOpt <- repository.getUserAccountById(toAccountId)
      
      // Валидация существования счетов
      fromAccount <- ZIO.fromOption(fromAccountOpt)
        .mapError(_ => new RuntimeException(s"Source account with id $fromAccountId not found"))
      toAccount <- ZIO.fromOption(toAccountOpt)
        .mapError(_ => new RuntimeException(s"Destination account with id $toAccountId not found"))
      
      // Валидация достаточности средств
      _ <- ZIO.fail(new RuntimeException(s"Insufficient balance. Required: $amount, Available: ${fromAccount.balance}"))
        .when(fromAccount.balance < amount)
      
      // Валидация положительной суммы
      _ <- ZIO.fail(new RuntimeException("Transaction amount must be positive"))
        .when(amount <= 0)
      
      // Выполняем транзакцию атомарно
      _ <- ZIO.collectAllPar(
        List(
          repository.updateUserAccount(fromAccount.copy(balance = fromAccount.balance - amount)),
          repository.updateUserAccount(toAccount.copy(balance = toAccount.balance + amount))
        )
      ).unit
      
    } yield ()
  }

}


object DataService {
  def listUserAccounts: ZIO[DataSource with DataService, Throwable, List[UserAccount]] =
    ZIO.service[DataService].flatMap(_.listUserAccounts)
    
  def updateUserAccount(userAccount: UserAccount): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.updateUserAccount(userAccount))
    
  def provideTransaction(fromAccountId: java.util.UUID, toAccountId: java.util.UUID, amount: Double): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.provideTransaction(fromAccountId, toAccountId, amount))
    
  val live: ZLayer[DataRepository, Nothing, DataService] = 
    ZLayer.fromFunction(new DataServiceImpl(_))
}
