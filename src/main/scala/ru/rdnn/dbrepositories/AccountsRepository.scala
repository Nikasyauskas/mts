package ru.rdnn.dbrepositories

import ru.rdnn.db
import ru.rdnn.db.Ctx
import ru.rdnn.AppError
import ru.rdnn.AppError.{AccountNotFound, DbError}
import io.getquill.*
import zio.{ZIO, ZLayer}

import java.util.UUID
import javax.sql.DataSource

trait AccountsRepository {
  def findAccountByAccountNumber(accountNumber: String): ZIO[DataSource, AppError, Accounts]
  def listAccountNumbersByUserId(userId: UUID): ZIO[DataSource, AppError, List[String]]
  def insertAccount(account: Accounts): ZIO[DataSource, AppError, Unit]
  def withdrawalAccount(account: Accounts, amount: Float): ZIO[DataSource, AppError, Unit] // списание
  def creditAccount(account: Accounts, amount: Float): ZIO[DataSource, AppError, Unit]     // зачисление
}

class AccountsRepositoryImpl(dataSource: DataSource) extends AccountsRepository {
  import Ctx.*

  private inline def bankAccountsSchema: Quoted[EntityQuery[Accounts]] = quote {
    querySchema[Accounts]("""bank.accounts""")
  }

  def findAccountByAccountNumber(accountNumber: String): ZIO[DataSource, AppError, Accounts] =
    ZIO
      .service[DataSource]
      .flatMap { _ =>
        run {
          bankAccountsSchema.filter(_.account_number == lift(accountNumber))
        }
      }
      .mapError(DbError(_))
      .flatMap { accounts =>
        ZIO
          .fromOption(accounts.headOption)
          .orElseFail(AccountNotFound(accountNumber))
      }

  def listAccountNumbersByUserId(userId: UUID): ZIO[DataSource, AppError, List[String]] =
    ZIO
      .service[DataSource]
      .flatMap { _ =>
        run {
          bankAccountsSchema
            .filter(a => a.user_id == lift(userId) && a.is_active == lift(true))
            .sortBy(_.account_number)(Ord.asc)
            .map(_.account_number)
        }
      }
      .mapError(DbError(_))

  def insertAccount(account: Accounts): ZIO[DataSource, AppError, Unit] =
    ZIO
      .service[DataSource]
      .flatMap { _ =>
        run(bankAccountsSchema.insertValue(lift(account))).unit
      }
      .mapError(DbError(_))

  def withdrawalAccount(account: Accounts, amount: Float): ZIO[DataSource, AppError, Unit] =
    ZIO
      .service[DataSource]
      .flatMap { _ =>
        run {
          bankAccountsSchema
            .filter(_.id == lift(account.id))
            .update(_.balance -> lift(account.balance - amount))
        }.unit
      }
      .mapError(DbError(_))

  def creditAccount(account: Accounts, amount: Float): ZIO[DataSource, AppError, Unit] =
    ZIO
      .service[DataSource]
      .flatMap { _ =>
        run {
          bankAccountsSchema
            .filter(_.id == lift(account.id))
            .update(_.balance -> lift(account.balance + amount))
        }.unit
      }
      .mapError(DbError(_))
}

object AccountsRepository {
  val live: ZLayer[DataSource, Nothing, AccountsRepository] = ZLayer.fromFunction(new AccountsRepositoryImpl(_))
}
