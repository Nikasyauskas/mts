package ru.rdnn.dto

import ru.rdnn.db
import zio.{ZIO, ZLayer}

import javax.sql.DataSource

trait TransactionsRepository {
  def insertTransaction(transaction: Transactions): ZIO[DataSource, Throwable, Unit]
}

class TransactionsRepositoryImpl(dataSource: DataSource) extends TransactionsRepository {
  private val ctx = db.Ctx
  import ctx._

  private lazy val backTransactionsSchema = quote {
    querySchema[Transactions]("""bank.transactions""")
  }

  override def insertTransaction(transaction: Transactions): ZIO[DataSource, Throwable, Unit] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx.run(
        backTransactionsSchema
          .insertValue(lift(transaction))
      ).unit
    }
}

object TransactionsRepository {
  val live: ZLayer[DataSource, Nothing, TransactionsRepository] = ZLayer.fromFunction(new TransactionsRepositoryImpl(_))
}

