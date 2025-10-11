package ru.rdnn

import zio.json.{DeriveJsonDecoder, JsonDecoder}
import java.util.UUID
import java.time.ZonedDateTime


package object dto {

  case class UserAccount(id: UUID, account_number: String, balance: Double)

  case class Transactions(
    id: UUID,
    from_account_id: String,
    to_account_id: String,
    amount: Double
  )

  case class TransferRequest(
    fromAccountId: UUID,
    toAccountId: UUID,
    amount: Double
  )

  case class BalanceHistory(
    id: UUID,
    account_number: String,
    old_balance: Double,
    new_balance: Double,
    amount: Double,
    created_at: ZonedDateTime
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
