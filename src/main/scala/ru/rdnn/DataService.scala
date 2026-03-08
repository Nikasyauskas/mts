package ru.rdnn

import ru.rdnn.dto.{AccountsRepository, BalanceHistory, BalanceHistoryRepository, Transactions, TransactionsRepository, TransferRequest, User, UserRepository}
import zio.{ZIO, ZLayer}

import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

trait DataService {
  def updateAccounts(transferRequest: TransferRequest): ZIO[DataSource, Throwable, Unit]
  def commitTransaction(transferRequest: TransferRequest): ZIO[DataSource, Throwable, Unit]
  def updateBalanceHistory(transferRequest: TransferRequest): ZIO[DataSource, Throwable, Unit]
  def provideTransaction(transferRequest: TransferRequest): ZIO[DataSource with DataService, Throwable, Unit]
}

class DataServiceImpl(
  userRepository: UserRepository,
  accountsRepository: AccountsRepository,
  transactionsRepository: TransactionsRepository,
  balanceHistoryRepository: BalanceHistoryRepository
) extends DataService {

  def updateAccounts(transferRequest: TransferRequest): ZIO[DataSource, Throwable, Unit] =
    for {
      accountFrom <- accountsRepository.findAccountByAccountNumber(transferRequest.fromAccount)
      accountTo <- accountsRepository.findAccountByAccountNumber(transferRequest.toAccount)
      _ <- ZIO
        .fail(new RuntimeException(s"Insufficient balance. Required: ${transferRequest.amount}, Available: ${accountFrom.balance}"))
        .when(accountFrom.balance < transferRequest.amount)
      _ <- ZIO
        .fail(new RuntimeException("Transaction amount must be positive"))
        .when(transferRequest.amount <= 0)
      // // TODO: Execute the transaction atomically. check it in otus.ru project or John'De'Goes
      _ <- accountsRepository.withdrawalAccount(accountFrom, transferRequest.amount)
      _ <- accountsRepository.creditAccount(accountTo, transferRequest.amount)
    } yield ()

  def commitTransaction(transferRequest: TransferRequest): ZIO[DataSource, Throwable, Unit] = for {
    transaction <- ZIO.attempt {
      Transactions(
        id = UUID.randomUUID(),
        from_account = transferRequest.fromAccount,
        to_account = transferRequest.toAccount,
        amount = transferRequest.amount,
        currency_code = "RUB",
        exchange_rate = 00.0.toFloat, // TODO "stub" for adding the exchange_rate parameter in the future
        created_at = ZonedDateTime.now()
      )
    }
      _ <- transactionsRepository.insertTransaction(transaction)
  } yield ()

  def updateBalanceHistory(transferRequest: TransferRequest): ZIO[DataSource, Throwable, Unit] = for {
    balanceHistory <- balanceHistoryRepository.findBalanceByAccountNumbers(transferRequest)
    balanceHistoryFrom <- ZIO.attempt {
      BalanceHistory(
        id = UUID.randomUUID(),
        account_number = transferRequest.fromAccount,
        old_balance = balanceHistory._1.new_balance,
        new_balance = balanceHistory._1.new_balance - transferRequest.amount,
        amount = transferRequest.amount,
        created_at = ZonedDateTime.now()
      )
    }
    balanceHistoryTo <- ZIO.attempt {
      BalanceHistory(
        id = UUID.randomUUID(),
        account_number = transferRequest.toAccount,
        old_balance = balanceHistory._2.new_balance,
        new_balance = balanceHistory._2.new_balance + transferRequest.amount,
        amount = transferRequest.amount,
        created_at = ZonedDateTime.now()
      )
    }
    _ <- balanceHistoryRepository.insertNewBalance(balanceHistoryFrom)
    _ <- balanceHistoryRepository.insertNewBalance(balanceHistoryTo)
  } yield ()

  def provideTransaction(transferRequest: TransferRequest): ZIO[DataSource with DataService, Throwable, Unit] = for {
    _ <- updateAccounts(transferRequest)
    _ <- commitTransaction(transferRequest)
    _ <- updateBalanceHistory(transferRequest)
  } yield ()

}

object DataService {

  def updateAccounts(transferRequest: TransferRequest): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.updateAccounts(transferRequest))

  def commitTransaction(transferRequest: TransferRequest): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.commitTransaction(transferRequest))

  def updateBalanceHistory(transferRequest: TransferRequest): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.updateBalanceHistory(transferRequest))

  def provideTransaction(transferRequest: TransferRequest): ZIO[DataSource with DataService, Throwable, Unit] =
    ZIO.service[DataService].flatMap(_.provideTransaction(transferRequest))

  val live: ZLayer[UserRepository with AccountsRepository with TransactionsRepository with BalanceHistoryRepository, Nothing, DataService] =
    ZLayer.fromFunction(
      (userRepo: UserRepository, accountsRepo: AccountsRepository,transactionsRepo: TransactionsRepository,balanceHistoryRepo: BalanceHistoryRepository) =>
        new DataServiceImpl(userRepo, accountsRepo, transactionsRepo, balanceHistoryRepo)
    )
}
