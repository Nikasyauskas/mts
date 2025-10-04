package ru.rdnn

import zio.json.{DeriveJsonDecoder, JsonDecoder}
import java.util.UUID

package object dto {

  case class UserAccount(id: UUID, account_number: String, balance: Double)


  case class TransferRequest(
    fromAccountId: UUID,
    toAccountId: UUID,
    amount: Double
  )

  object TransferRequest {
    implicit val transferRequestDecoder: JsonDecoder[TransferRequest] = DeriveJsonDecoder.gen[TransferRequest]
  }

  case class TransferRequestByAN(
    fromAccount: String,
    toAccount: String,
    amount: Double
  )

  object TransferRequestByAN {
    implicit val transferRequestDecoder: JsonDecoder[TransferRequestByAN] = DeriveJsonDecoder.gen[TransferRequestByAN]
  }

}
