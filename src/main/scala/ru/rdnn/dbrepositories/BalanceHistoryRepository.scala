package ru.rdnn.dbrepositories

import io.getquill.Ord
import ru.rdnn.db
import zio.{ZIO, ZLayer}

import javax.sql.DataSource

trait BalanceHistoryRepository {
  def insertNewBalance(newBalance: BalanceHistory): ZIO[DataSource, Throwable, Unit]
  def findBalanceByAccountNumbers(transactionRequest: TransferRequest): ZIO[DataSource, Throwable, (BalanceHistory, BalanceHistory)]
}

class BalanceHistoryRepositoryImpl(dataSource: DataSource) extends BalanceHistoryRepository {
  private val ctx = db.Ctx
  import ctx._

  private lazy val bankBalanceHistorySchema = quote {
    querySchema[BalanceHistory]("""bank.balance_history""")
  }

  def insertNewBalance(newBalance: BalanceHistory): ZIO[DataSource, Throwable, Unit] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx
        .run(
          bankBalanceHistorySchema
            .insertValue(lift(newBalance))
        )
        .unit
    }

  def findBalanceByAccountNumbers(transactionRequest: TransferRequest): ZIO[DataSource, Throwable, (BalanceHistory, BalanceHistory)] =
    ZIO.service[DataSource].flatMap { ds =>
      for {
        fromBalance <- ctx
          .run(
            bankBalanceHistorySchema
              .filter(_.account_number == lift(transactionRequest.fromAccount))
              .sortBy(_.created_at)(Ord.desc)
              .take(1) // TODO возможно все испортит, т.к. может быть take на NULL
          )
        toBalance <- ctx
          .run(
            bankBalanceHistorySchema
              .filter(_.account_number == lift(transactionRequest.toAccount))
              .sortBy(_.created_at)(Ord.desc)
              .take(1) // TODO возможно все испортит, т.к. может быть take на NULL
          )
      } yield (fromBalance.head, toBalance.head)
    }

}

object BalanceHistoryRepository {

  val live: ZLayer[DataSource, Nothing, BalanceHistoryRepository] =
    ZLayer.fromFunction(new BalanceHistoryRepositoryImpl(_))
}
