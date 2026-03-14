package ru.rdnn.dbrepositories

import io.getquill.Ord
import ru.rdnn.db
import ru.rdnn.db.Ctx
import io.getquill.*
import zio.{ZIO, ZLayer}
import ru.rdnn.AppError
import ru.rdnn.AppError.{BalanceHistoryNotFound, DbError}

import javax.sql.DataSource

trait BalanceHistoryRepository {
  def insertNewBalance(newBalance: BalanceHistory): ZIO[DataSource, AppError, Unit]
  def findBalanceByAccountNumbers(transactionRequest: TransferRequest): ZIO[DataSource, AppError, (BalanceHistory, BalanceHistory)]
}

class BalanceHistoryRepositoryImpl(dataSource: DataSource) extends BalanceHistoryRepository {
  import Ctx.*

  private inline def bankBalanceHistorySchema: Quoted[EntityQuery[BalanceHistory]] = quote {
    querySchema[BalanceHistory]("""bank.balance_history""")
  }

  def insertNewBalance(newBalance: BalanceHistory): ZIO[DataSource, AppError, Unit] =
    ZIO
      .service[DataSource]
      .flatMap { _ =>
        run(
          bankBalanceHistorySchema
            .insertValue(lift(newBalance))
        ).unit
      }
      .mapError(DbError(_))

  def findBalanceByAccountNumbers(transactionRequest: TransferRequest): ZIO[DataSource, AppError, (BalanceHistory, BalanceHistory)] =
    ZIO
      .service[DataSource]
      .flatMap { _ =>
        for {
          fromBalance <- run(
              bankBalanceHistorySchema
                .filter(_.account_number == lift(transactionRequest.fromAccount))
                .sortBy(_.created_at)(Ord.desc)
                .take(1)
            )
          toBalance <- run(
              bankBalanceHistorySchema
                .filter(_.account_number == lift(transactionRequest.toAccount))
                .sortBy(_.created_at)(Ord.desc)
                .take(1)
            )
        } yield (fromBalance.headOption, toBalance.headOption)
      }
      .mapError(DbError(_))
      .flatMap {
        case (Some(from), Some(to)) =>
          ZIO.succeed((from, to))
        case (None, _) =>
          ZIO.fail(BalanceHistoryNotFound(transactionRequest.fromAccount))
        case (_, None) =>
          ZIO.fail(BalanceHistoryNotFound(transactionRequest.toAccount))
      }

}

object BalanceHistoryRepository {

  val live: ZLayer[DataSource, Nothing, BalanceHistoryRepository] =
    ZLayer.fromFunction(new BalanceHistoryRepositoryImpl(_))
}
