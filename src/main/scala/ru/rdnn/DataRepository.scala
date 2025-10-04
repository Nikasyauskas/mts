package ru.rdnn

import zio.{ZIO, ZLayer}
import ru.rdnn.dto.UserAccount

import java.util.UUID
import javax.sql.DataSource



trait DataRepository {
  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]]
  def updateUserAccount(userAccount: UserAccount): ZIO[DataSource, Throwable, Unit]
  def findAccountById(id: UUID): ZIO[DataSource, Throwable, Option[UserAccount]]
  def findByAccountNumber(accountNumber: String): ZIO[DataSource, Throwable, Option[UserAccount]]
}

class Impl(dataSource: DataSource) extends DataRepository {
  private val ctx = db.Ctx
  import ctx._

  private lazy val backUsersSchema = quote {
    querySchema[UserAccount]("""bank.users""")
  }

  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx.run(backUsersSchema).provide(ZLayer.succeed(ds))
    }

  def updateUserAccount(account: UserAccount): ZIO[DataSource, Throwable, Unit] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx
        .run(
          backUsersSchema
            .filter(_.id == lift(account.id))
            .filter(_.account_number == lift(account.account_number))
            .updateValue(lift(account))
        )
        .unit
        .provide(ZLayer.succeed(ds))
    }

  def findAccountById(id: java.util.UUID): ZIO[DataSource, Throwable, Option[UserAccount]] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx
        .run(
          backUsersSchema
            .filter(_.id == lift(id))
        )
        .map(_.headOption)
        .provide(ZLayer.succeed(ds))
    }

  override def findByAccountNumber(accountNumber: String): ZIO[DataSource, Throwable, Option[UserAccount]] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx
        .run(
          backUsersSchema
            .filter(_.account_number == lift(accountNumber))
        )
        .map(_.headOption)
        .provide(ZLayer.succeed(ds))
    }
}

object DataRepository {
  val live: ZLayer[DataSource, Nothing, DataRepository] = ZLayer.fromFunction(new Impl(_))
}
