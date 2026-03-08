package ru.rdnn

import zio.json.{DeriveJsonDecoder, JsonDecoder}
import java.util.UUID
import java.time.ZonedDateTime

package object dbrepositories {

  case class User(
    id: UUID,
    user_name: String,
    email: String,
    phone: String,
    created_at: ZonedDateTime,
    updated_at: ZonedDateTime,
    is_active: Boolean
  )

  case class Accounts(
    id: UUID,
    user_id: UUID,
    account_number: String,
    currency_code: String,
    balance: Float,
    created_at: ZonedDateTime,
    updated_at: ZonedDateTime,
    is_active: Boolean
  )

  case class Transactions(
    id: UUID,
    from_account: String,
    to_account: String,
    amount: Float,
    currency_code: String,
    exchange_rate: Float,
    created_at: ZonedDateTime
  )

  case class BalanceHistory(
    id: UUID,
    account_number: String,
    old_balance: Float,
    new_balance: Float,
    amount: Float,
    created_at: ZonedDateTime
  )

  case class TransferRequest(
    userId: String,
    fromAccount: String,
    toAccount: String,
    amount: Float
  )

  object TransferRequest {
    implicit val transferRequestDecoder: JsonDecoder[TransferRequest] = DeriveJsonDecoder.gen[TransferRequest]
  }

}
