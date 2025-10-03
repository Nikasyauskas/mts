package ru.rdnn.api

import zio._
import ru.rdnn.DataService
import ru.rdnn.dto.TransferRequest
import zio.http.{Response, _}
import zio.json._

import javax.sql.DataSource

object MoneyTransferAPI {

  val api: Routes[DataSource with DataService, Nothing] = Routes(
    Method.POST / "transfer" -> handler { (req: Request) =>
      (
        for {
          body <- req.body.asString
          transferRequest <- ZIO
            .fromEither(body.fromJson[TransferRequest])
            .mapError(err => new Exception(s"Invalid JSON: $err"))
          _ <- DataService.provideTransaction(
            transferRequest.fromAccountId,
            transferRequest.toAccountId,
            transferRequest.amount
          )
        } yield Response.json("""Transfer completed successfully""")
      )
        .catchAll { error =>
          ZIO.succeed(Response.badRequest(s"Error: ${error.getMessage}"))
        }
    }
  )
}
