package ru.rdnn.dbrepositories

import ru.rdnn.db
import ru.rdnn.db.Ctx
import io.getquill.*
import zio.{ZIO, ZLayer}

import javax.sql.DataSource

trait TransactionsRepository {
  def insertTransaction(transaction: Transactions): ZIO[DataSource, Throwable, Unit]
}

class TransactionsRepositoryImpl(dataSource: DataSource) extends TransactionsRepository {
  import Ctx.*

  private inline def backTransactionsSchema: Quoted[EntityQuery[Transactions]] = quote {
    querySchema[Transactions]("""bank.transactions""")
  }

  override def insertTransaction(transaction: Transactions): ZIO[DataSource, Throwable, Unit] =
    ZIO.service[DataSource].flatMap { _ =>
      run(
        backTransactionsSchema
          .insertValue(lift(transaction))
      ).unit
    }
}

object TransactionsRepository {
  val live: ZLayer[DataSource, Nothing, TransactionsRepository] = ZLayer.fromFunction(new TransactionsRepositoryImpl(_))
}

