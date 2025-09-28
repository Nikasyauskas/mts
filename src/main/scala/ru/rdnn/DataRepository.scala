package ru.rdnn

import io.getquill.context.ZioJdbc.QIO
import zio.{ULayer, ZLayer}
import ru.rdnn.db

case class UserAccount(id: String, account_number: String, balance: Double)

trait DataRepository {
  def listUserAccounts: QIO[List[UserAccount]]
}

class Impl extends DataRepository {
  private val ctx = db.Ctx
  import ctx._

  private lazy val backUsersSchema = quote {
    querySchema[UserAccount]("""bank.users""")
  }

  def listUserAccounts: QIO[List[UserAccount]] = ctx.run(backUsersSchema)
}

object DataRepository {
  val live: ULayer[DataRepository] = ZLayer.succeed(new Impl)
}
