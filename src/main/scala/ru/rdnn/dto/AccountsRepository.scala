package ru.rdnn.dto

import ru.rdnn.db
import zio.{ZIO, ZLayer}

import javax.sql.DataSource

trait AccountsRepository {
  def findAccountByAccountNumber(accountNumber: String): ZIO[DataSource, Throwable, Accounts]
  def withdrawalAccount(account: Accounts, amount: Float): ZIO[DataSource, Throwable, Unit] // списание
  def creditAccount(account: Accounts, amount: Float): ZIO[DataSource, Throwable, Unit]     // зачисление
}

class AccountsRepositoryImpl(dataSource: DataSource) extends AccountsRepository {
  private val ctx = db.Ctx
  import ctx._

  private lazy val bankAccountsSchema = quote {
    querySchema[Accounts]("""bank.accounts""")
  }

  def findAccountByAccountNumber(accountNumber: String): ZIO[DataSource, Throwable, Accounts] =
    ZIO.service[DataSource].flatMap { _ =>
      ctx
        .run(
          quote(bankAccountsSchema.filter(_.account_number == lift(accountNumber)))
        )
        .map(_.head)
    }

  def withdrawalAccount(account: Accounts, amount: Float): ZIO[DataSource, Throwable, Unit] =
    ZIO.service[DataSource].flatMap { _ =>
      ctx.run(
        quote(
          bankAccountsSchema
            .filter(_.id == lift(account.id))
            .update(_.balance -> lift(account.balance - amount))
        )
      ).unit
    }

  def creditAccount(account: Accounts, amount: Float): ZIO[DataSource, Throwable, Unit] =
    ZIO.service[DataSource].flatMap { _ =>
      ctx.run(
        quote(
          bankAccountsSchema
            .filter(_.id == lift(account.id))
            .update(_.balance -> lift(account.balance + amount))
        )
      ).unit
    }
}

object AccountsRepository {
  val live: ZLayer[DataSource, Nothing, AccountsRepository] = ZLayer.fromFunction(new AccountsRepositoryImpl(_))
}
