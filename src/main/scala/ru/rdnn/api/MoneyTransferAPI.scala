package ru.rdnn.api

import zio._
import ru.rdnn.DataService
import ru.rdnn.dto.{BalanceHistory, Transactions, TransferRequest, TransferRequestByAN}
import zio.http.{Response, _}
import zio.json._
import java.time.ZonedDateTime

import javax.sql.DataSource

object MoneyTransferAPI {

  val api: Routes[DataSource with DataService, Nothing] = Routes(
    Method.POST / "transfer" / "account-number" -> handler { (req: Request) =>
      (
        for {
          body <- req.body.asString
          transferRequest <- ZIO
            .fromEither(body.fromJson[TransferRequestByAN])
            .mapError(err => new Exception(s"Invalid JSON: $err"))
          _ <- DataService.transactionComplete(transferRequest)
        } yield Response.json(s"""Transfer completed successfully\n""")
      )
        .catchAll { error =>
          ZIO.succeed(Response.badRequest(s"Error: ${error.getMessage}"))
        }
    }
  )
}
