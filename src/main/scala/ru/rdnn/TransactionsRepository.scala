package ru.rdnn

import ru.rdnn.dto.{Transactions, UserAccount}
import zio.{ULayer, ZIO, ZLayer}

import java.util.UUID
import javax.sql.DataSource

trait TransactionsRepository {
  def listTransactions: ZIO[DataSource, Throwable, List[Transactions]]
  def insertTransaction(transaction: Transactions): ZIO[DataSource, Throwable, Unit]
}

class TransactionsRepositoryImpl(dataSource: DataSource) extends TransactionsRepository {
  private val ctx = db.Ctx
  import ctx._

  private lazy val backTransactionsSchema = quote {
    querySchema[Transactions]("""bank.transactions""")
  }
  override def listTransactions: ZIO[DataSource, Throwable, List[Transactions]] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx.run(backTransactionsSchema).provide(ZLayer.succeed(ds))
    }

  override def insertTransaction(transaction: Transactions): ZIO[DataSource, Throwable, Unit] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx.run(
        backTransactionsSchema
          .insertValue(lift(transaction))
      ).unit.provide(ZLayer.succeed(ds))
    }
}

object TransactionsRepository {
  val live: ZLayer[DataSource, Nothing, TransactionsRepository] = ZLayer.fromFunction(new TransactionsRepositoryImpl(_))
}

