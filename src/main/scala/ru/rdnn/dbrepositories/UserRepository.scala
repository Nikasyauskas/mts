package ru.rdnn.dbrepositories

import ru.rdnn.db
import ru.rdnn.db.Ctx
import io.getquill.*
import zio.{ZIO, ZLayer}
import ru.rdnn.AppError
import ru.rdnn.AppError.DbError

import java.util.UUID
import javax.sql.DataSource



trait UserRepository {
  def findUserById(id: UUID): ZIO[DataSource, AppError, Option[User]]
}

class UserRepositoryImpl(dataSource: DataSource) extends UserRepository {
  import Ctx.*

  private inline def bankUsersSchema = quote {
    querySchema[User]("""bank.users""")
  }

  def findUserById(id: java.util.UUID): ZIO[DataSource, AppError, Option[User]] =
    ZIO
      .service[DataSource]
      .flatMap { _ =>
        run(
          bankUsersSchema
            .filter(_.id == lift(id))
        ).map(_.headOption)
      }
      .mapError(DbError(_))

}

object UserRepository {
  val live: ZLayer[DataSource, Nothing, UserRepository] = ZLayer.fromFunction(new UserRepositoryImpl(_))
}
