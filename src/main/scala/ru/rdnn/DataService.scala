package ru.rdnn

import ru.rdnn.dto.{BalanceHistory, BalanceHistoryRepository, Transactions, TransactionsRepository, TransferRequestByAN, UserAccount, UserRepository}
import zio.{ZIO, ZLayer}

import javax.sql.DataSource

trait DataService {
  def findUserByAccountNumber(accountNumber: String): ZIO[DataSource, Throwable, Option[UserAccount]]
  def provideTransaction(transferRequest: TransferRequestByAN): ZIO[DataSource, Throwable, Unit]
  def insertTransaction(transaction: Transactions): ZIO[DataSource, Throwable, Unit]
  def insertBalanceHistory(newBalance: BalanceHistory): ZIO[DataSource, Throwable, Unit]
  def findBalanceByAccountNumbers(
    accountFrom: String,
    accountTo: String
  ): ZIO[DataSource, Throwable, (BalanceHistory, BalanceHistory)]
}

class DataServiceImpl(
  repository: UserRepository,
  transactionsRepository: TransactionsRepository,
  balanceHistoryRepository: BalanceHistoryRepository
) extends DataService {

  def findUserByAccountNumber(accountNumber: String): ZIO[DataSource, Throwable, Option[UserAccount]] =
    repository.findByAccountNumber(accountNumber)

  def provideTransaction(transferRequest: TransferRequestByAN): ZIO[DataSource, Throwable, Unit] =
    for {
      fromAccountOpt <- repository.findByAccountNumber(transferRequest.fromAccount)
      toAccountOpt   <- repository.findByAccountNumber(transferRequest.toAccount)
      fromAccount <- ZIO
        .fromOption(fromAccountOpt) // TODO: exception hierarchy
        .mapError(_ => new RuntimeException(s"Source account with id ${transferRequest.fromAccount} not found"))
      toAccount <- ZIO
        .fromOption(toAccountOpt) // TODO: exception hierarchy
        .mapError(_ => new RuntimeException(s"Destination account with id ${transferRequest.toAccount} not found"))
      _ <- ZIO
        .fail(new RuntimeException(s"Insufficient balance. Required: ${transferRequest.amount}, Available: ${fromAccount.balance}"))
        .when(fromAccount.balance < transferRequest.amount)
      _ <- ZIO
        .fail(new RuntimeException("Transaction amount must be positive"))
        .when(transferRequest.amount <= 0)
      // // TODO: Execute the transaction atomically. check it in otus.ru project or John'De'Goes
      _ <- ZIO
        .collectAllPar(
          List(
            repository.updateUserAccount(fromAccount.copy(balance = fromAccount.balance - transferRequest.amount)),
            repository.updateUserAccount(toAccount.copy(balance = toAccount.balance + transferRequest.amount))
          )
        )
        .unit
    } yield ()

  def insertTransaction(transaction: Transactions): ZIO[DataSource, Throwable, Unit] =
    transactionsRepository.insertTransaction(transaction)

  def insertBalanceHistory(newBalance: BalanceHistory): ZIO[DataSource, Throwable, Unit] =
    balanceHistoryRepository.insertNewBalance(newBalance)

  def findBalanceByAccountNumbers(
    accountFrom: String,
    accountTo: String
  ): ZIO[DataSource, Throwable, (BalanceHistory, BalanceHistory)] =
    balanceHistoryRepository.findBalanceByAccountNumbers(accountFrom, accountTo)

}

object DataService {

  def findUserByAccountNumber(accountNumber: String): ZIO[DataSource with DataService, Throwable, Option[UserAccount]] =
    ZIO.service[DataService].flatMap(_.findUserByAccountNumber(accountNumber))

  def provideTransaction(transferRequest: TransferRequestByAN): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.provideTransaction(transferRequest))

  def insertTransaction(transaction: Transactions): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.insertTransaction(transaction))

  def insertBalanceHistory(newBalance: BalanceHistory): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.insertBalanceHistory(newBalance))

  def findBalanceByAccountNumbers(
    accountFrom: String,
    accountTo: String
  ): ZIO[DataSource with DataService, Throwable, (BalanceHistory, BalanceHistory)] =
    ZIO.service[DataService].flatMap(_.findBalanceByAccountNumbers(accountFrom, accountTo))

  val live: ZLayer[UserRepository with TransactionsRepository with BalanceHistoryRepository, Nothing, DataService] =
    ZLayer.fromFunction(
      (userRepo: UserRepository,
       transactionsRepo: TransactionsRepository,
       balanceHistoryRepository: BalanceHistoryRepository
      ) => new DataServiceImpl(userRepo, transactionsRepo, balanceHistoryRepository)
    )
}
