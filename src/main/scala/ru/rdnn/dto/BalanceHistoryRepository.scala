package ru.rdnn.dto

import io.getquill.Ord
import ru.rdnn.db
import zio.{ZIO, ZLayer}

import javax.sql.DataSource

trait BalanceHistoryRepository {
  def listBalanceHistory: ZIO[DataSource, Throwable, List[BalanceHistory]]
  def insertNewBalance(newBalance: BalanceHistory): ZIO[DataSource, Throwable, Unit]
  def findBalanceByAccountNumbers(accountFrom: String,
                                  accountTo: String
  ): ZIO[DataSource, Throwable, (BalanceHistory, BalanceHistory)]
}

class BalanceHistoryRepositoryImpl(dataSource: DataSource) extends BalanceHistoryRepository {
  private val ctx = db.Ctx
  import ctx._

  private lazy val bankBalanceHistorySchema = quote {
    querySchema[BalanceHistory]("""bank.balance_history""")
  }

  override def listBalanceHistory: ZIO[DataSource, Throwable, List[BalanceHistory]] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx.run(bankBalanceHistorySchema)
    }

  override def insertNewBalance(newBalance: BalanceHistory): ZIO[DataSource, Throwable, Unit] =
    ZIO.service[DataSource].flatMap { ds =>
      ctx
        .run(
          bankBalanceHistorySchema
            .insertValue(lift(newBalance))
        )
        .unit
    }

  override def findBalanceByAccountNumbers(
    accountFrom: String,
    accountTo: String
  ): ZIO[DataSource, Throwable, (BalanceHistory, BalanceHistory)] =
    ZIO.service[DataSource].flatMap { ds =>
      for {
        fromBalance <- ctx
          .run(
            bankBalanceHistorySchema
              .filter(_.account_number == lift(accountFrom))
              .sortBy(_.created_at)(Ord.desc)
              .take(1)
          )
        toBalance <- ctx
          .run(
            bankBalanceHistorySchema
              .filter(_.account_number == lift(accountTo))
              .sortBy(_.created_at)(Ord.desc)
              .take(1)
          )
      } yield (fromBalance.head, toBalance.head)
    }

}

object BalanceHistoryRepository {

  val live: ZLayer[DataSource, Nothing, BalanceHistoryRepository] =
    ZLayer.fromFunction(new BalanceHistoryRepositoryImpl(_))
}
