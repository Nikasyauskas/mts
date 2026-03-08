package ru.rdnn.api

import zio._
import ru.rdnn.DataBaseService
import ru.rdnn.dbrepositories.TransferRequest
import zio.http._
import zio.json._

import javax.sql.DataSource

object MoneyTransferAPI {

  val api: Routes[DataSource with DataBaseService, Nothing] = Routes(
    Method.POST / "transfer" / "account-number" -> handler { (req: Request) =>
      (
        for {
          body <- req.body.asString
          transferRequest <- ZIO
            .fromEither(body.fromJson[TransferRequest])
            .mapError(err => new Exception(s"Invalid JSON: $err"))
          _ <- DataBaseService.provideTransaction(transferRequest)
        } yield Response.json(s"""Transfer completed successfully\n""")
      )
        .catchAll { error =>
          ZIO.succeed(Response.badRequest(s"Error: ${error.getMessage}"))
        }
    }
  )
}
