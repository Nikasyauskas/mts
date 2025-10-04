package ru.rdnn

import ru.rdnn.dto.UserAccount
import zio.{ZIO, ZLayer}

import javax.sql.DataSource

trait DataService {
  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]]
  def updateUserAccount(userAccount: UserAccount): ZIO[DataSource, Throwable, Unit]
  def provideTransaction(fromAccountId: java.util.UUID, toAccountId: java.util.UUID, amount: Double): ZIO[DataSource, Throwable, Unit]
  def provideTransaction(fromAccount: String, toAccount: String, amount: Double): ZIO[DataSource,Throwable,Unit]
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
      fromAccountOpt <- repository.findAccountById(fromAccountId)
      toAccountOpt <- repository.findAccountById(toAccountId)
      fromAccount <- ZIO.fromOption(fromAccountOpt) //TODO: exception hierarchy
        .mapError(_ => new RuntimeException(s"Source account with id $fromAccountId not found"))
      toAccount <- ZIO.fromOption(toAccountOpt)
        .mapError(_ => new RuntimeException(s"Destination account with id $toAccountId not found"))
      _ <- ZIO.fail(new RuntimeException(s"Insufficient balance. Required: $amount, Available: ${fromAccount.balance}"))
        .when(fromAccount.balance < amount)
      _ <- ZIO.fail(new RuntimeException("Transaction amount must be positive"))
        .when(amount <= 0)
      // Execute the transaction atomically //TODO: check it in otus.ru project
      _ <- ZIO.collectAllPar(
        List(
          repository.updateUserAccount(fromAccount.copy(balance = fromAccount.balance - amount)),
          repository.updateUserAccount(toAccount.copy(balance = toAccount.balance + amount))
        )
      ).unit
      
    } yield ()
  }

  def provideTransaction(fromAccount: String, toAccount: String, amount: Double): ZIO[DataSource, Throwable, Unit] = {
    for {
      fromAccountOpt <- repository.findByAccountNumber(fromAccount)
      toAccountOpt <- repository.findByAccountNumber(toAccount)
      fromAccount <- ZIO.fromOption(fromAccountOpt) //TODO: exception hierarchy
        .mapError(_ => new RuntimeException(s"Source account with id $fromAccount not found"))
      toAccount <- ZIO.fromOption(toAccountOpt)
        .mapError(_ => new RuntimeException(s"Destination account with id $toAccount not found"))
      _ <- ZIO.fail(new RuntimeException(s"Insufficient balance. Required: $amount, Available: ${fromAccount.balance}"))
        .when(fromAccount.balance < amount)
      _ <- ZIO.fail(new RuntimeException("Transaction amount must be positive"))
        .when(amount <= 0)
      // Execute the transaction atomically //TODO: check it in otus.ru project
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

  def provideTransaction(fromAccountId: String, toAccountId: String, amount: Double): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.provideTransaction(fromAccountId, toAccountId, amount))
    
  val live: ZLayer[DataRepository, Nothing, DataService] = 
    ZLayer.fromFunction(new DataServiceImpl(_))
}
