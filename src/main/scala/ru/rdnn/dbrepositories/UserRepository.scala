package ru.rdnn.dbrepositories

import ru.rdnn.db
import zio.{ZIO, ZLayer}

import java.util.UUID
import javax.sql.DataSource



trait UserRepository {
  def findUserById(id: UUID): ZIO[DataSource, Throwable, Option[User]]
}

class UserRepositoryImpl(dataSource: DataSource) extends UserRepository {
  private val ctx = db.Ctx
  import ctx._

  private lazy val bankUsersSchema = quote {
    querySchema[User]("""bank.users""")
  }

  def findUserById(id: java.util.UUID): ZIO[DataSource, Throwable, Option[User]] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx
        .run(
          bankUsersSchema
            .filter(_.id == lift(id))
        )
        .map(_.headOption)
    }

}

object UserRepository {
  val live: ZLayer[DataSource, Nothing, UserRepository] = ZLayer.fromFunction(new UserRepositoryImpl(_))
}
