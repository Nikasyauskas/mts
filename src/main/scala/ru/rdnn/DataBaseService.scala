package ru.rdnn

import ru.rdnn.dbrepositories.{AccountsRepository, BalanceHistory, BalanceHistoryRepository, Transactions, TransactionsRepository, TransferRequest, User, UserRepository}
import zio.{ZIO, ZLayer}
import ru.rdnn.AppError
import ru.rdnn.AppError.{DbError, InsufficientBalance, NonPositiveAmount}

import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

trait DataBaseService {
  def updateAccounts(transferRequest: TransferRequest): ZIO[DataSource, AppError, Unit]
  def commitTransaction(transferRequest: TransferRequest): ZIO[DataSource, AppError, Unit]
  def updateBalanceHistory(transferRequest: TransferRequest): ZIO[DataSource, AppError, Unit]
  def provideTransaction(transferRequest: TransferRequest): ZIO[DataSource with DataBaseService, AppError, Unit]
}

class DataBaseServiceImpl(
  userRepository: UserRepository,
  accountsRepository: AccountsRepository,
  transactionsRepository: TransactionsRepository,
  balanceHistoryRepository: BalanceHistoryRepository
) extends DataBaseService {

  def updateAccounts(transferRequest: TransferRequest): ZIO[DataSource, AppError, Unit] =
    for {
      accountFrom <- accountsRepository.findAccountByAccountNumber(transferRequest.fromAccount)
      accountTo <- accountsRepository.findAccountByAccountNumber(transferRequest.toAccount)
      _ <- ZIO
        .fail(InsufficientBalance(transferRequest.amount, accountFrom.balance): AppError)
        .when(accountFrom.balance < transferRequest.amount)
      _ <- ZIO
        .fail(NonPositiveAmount(transferRequest.amount): AppError)
        .when(transferRequest.amount <= 0)
      // // TODO: Execute the transaction atomically. check it in otus.ru project or John'De'Goes
      _ <- accountsRepository.withdrawalAccount(accountFrom, transferRequest.amount)
      _ <- accountsRepository.creditAccount(accountTo, transferRequest.amount)
    } yield ()

  def commitTransaction(transferRequest: TransferRequest): ZIO[DataSource, AppError, Unit] =
    for {
      transaction <-
        ZIO.succeed {
          Transactions(
            id = UUID.randomUUID(),
            from_account = transferRequest.fromAccount,
            to_account = transferRequest.toAccount,
            amount = transferRequest.amount,
            currency_code = "RUB",
            exchange_rate = 0.0f, // TODO "stub" for adding the exchange_rate parameter in the future
            created_at = ZonedDateTime.now()
          )
        }
      _ <- transactionsRepository.insertTransaction(transaction).mapError(DbError(_))
    } yield ()

  def updateBalanceHistory(transferRequest: TransferRequest): ZIO[DataSource, AppError, Unit] = for {
    balanceHistory <- balanceHistoryRepository.findBalanceByAccountNumbers(transferRequest)
    balanceHistoryFrom <-
      ZIO.succeed {
        BalanceHistory(
          id = UUID.randomUUID(),
          account_number = transferRequest.fromAccount,
          old_balance = balanceHistory._1.new_balance,
          new_balance = balanceHistory._1.new_balance - transferRequest.amount,
          amount = transferRequest.amount,
          created_at = ZonedDateTime.now()
        )
      }
    balanceHistoryTo <-
      ZIO.succeed {
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

  def provideTransaction(transferRequest: TransferRequest): ZIO[DataSource with DataBaseService, AppError, Unit] = for {
    _ <- updateAccounts(transferRequest)
    _ <- commitTransaction(transferRequest)
    _ <- updateBalanceHistory(transferRequest)
  } yield ()

}

object DataBaseService {

  def updateAccounts(transferRequest: TransferRequest): ZIO[DataSource with DataBaseService, AppError, Unit] =
    ZIO.service[DataBaseService].flatMap(_.updateAccounts(transferRequest))

  def commitTransaction(transferRequest: TransferRequest): ZIO[DataSource with DataBaseService, AppError, Unit] =
    ZIO.service[DataBaseService].flatMap(_.commitTransaction(transferRequest))

  def updateBalanceHistory(transferRequest: TransferRequest): ZIO[DataSource with DataBaseService, AppError, Unit] =
    ZIO.service[DataBaseService].flatMap(_.updateBalanceHistory(transferRequest))

  def provideTransaction(transferRequest: TransferRequest): ZIO[DataSource with DataBaseService, AppError, Unit] =
    ZIO.service[DataBaseService].flatMap(_.provideTransaction(transferRequest))

  val live: ZLayer[UserRepository with AccountsRepository with TransactionsRepository with BalanceHistoryRepository, Nothing, DataBaseService] =
    ZLayer.fromFunction(
      (userRepo: UserRepository, accountsRepo: AccountsRepository,transactionsRepo: TransactionsRepository,balanceHistoryRepo: BalanceHistoryRepository) =>
        new DataBaseServiceImpl(userRepo, accountsRepo, transactionsRepo, balanceHistoryRepo)
    )
}
