package ru.rdnn.dbrepositories

import io.getquill.Ord
import ru.rdnn.db
import ru.rdnn.db.Ctx
import io.getquill.*
import zio.{ZIO, ZLayer}

import javax.sql.DataSource

trait BalanceHistoryRepository {
  def insertNewBalance(newBalance: BalanceHistory): ZIO[DataSource, Throwable, Unit]
  def findBalanceByAccountNumbers(transactionRequest: TransferRequest): ZIO[DataSource, Throwable, (BalanceHistory, BalanceHistory)]
}

class BalanceHistoryRepositoryImpl(dataSource: DataSource) extends BalanceHistoryRepository {
  import Ctx.*

  private inline def bankBalanceHistorySchema = quote {
    querySchema[BalanceHistory]("""bank.balance_history""")
  }

  def insertNewBalance(newBalance: BalanceHistory): ZIO[DataSource, Throwable, Unit] =
    ZIO.service[DataSource].flatMap { _ =>
      run(
        bankBalanceHistorySchema
          .insertValue(lift(newBalance))
      ).unit
    }

  def findBalanceByAccountNumbers(transactionRequest: TransferRequest): ZIO[DataSource, Throwable, (BalanceHistory, BalanceHistory)] =
    ZIO.service[DataSource].flatMap { _ =>
      for {
        fromBalance <-
          run(
            bankBalanceHistorySchema
              .filter(_.account_number == lift(transactionRequest.fromAccount))
              .sortBy(_.created_at)(Ord.desc)
              .take(1) // TODO возможно все испортит, т.к. может быть take на NULL
          )
        toBalance <-
          run(
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
