package ru.rdnn

import zio.{ZIO, ZLayer}
import ru.rdnn.dto.UserAccount

import java.util.UUID
import javax.sql.DataSource



trait UserRepository {
  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]]
  def updateUserAccount(userAccount: UserAccount): ZIO[DataSource, Throwable, Unit]
  def findAccountById(id: UUID): ZIO[DataSource, Throwable, Option[UserAccount]]
  def findByAccountNumber(accountNumber: String): ZIO[DataSource, Throwable, Option[UserAccount]]
}

class UserRepositoryImpl(dataSource: DataSource) extends UserRepository {
  private val ctx = db.Ctx
  import ctx._

  private lazy val backUsersSchema = quote {
    querySchema[UserAccount]("""bank.users""")
  }

  def listUserAccounts: ZIO[DataSource, Throwable, List[UserAccount]] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx.run(backUsersSchema)
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
    }

  def findAccountById(id: java.util.UUID): ZIO[DataSource, Throwable, Option[UserAccount]] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx
        .run(
          backUsersSchema
            .filter(_.id == lift(id))
        )
        .map(_.headOption)
    }

  override def findByAccountNumber(accountNumber: String): ZIO[DataSource, Throwable, Option[UserAccount]] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx
        .run(
          backUsersSchema
            .filter(_.account_number == lift(accountNumber))
        )
        .map(_.headOption)
    }
}

object UserRepository {
  val live: ZLayer[DataSource, Nothing, UserRepository] = ZLayer.fromFunction(new UserRepositoryImpl(_))
}
