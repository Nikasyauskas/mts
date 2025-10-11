import zio._
import zio.http._
import zio.json._
import java.util.UUID

// Модели данных
case class TransferRequest(
  fromAccountId: UUID,
  toAccountId: UUID,
  amount: BigDecimal
)

object TransferRequest {
  implicit val codec: JsonCodec[TransferRequest] = DeriveJsonCodec.gen[TransferRequest]
}

case class TransferResponse(
  message: String,
  transactionId: UUID
)

object TransferResponse {
  implicit val codec: JsonCodec[TransferResponse] = DeriveJsonCodec.gen[TransferResponse]
}

case class ErrorResponse(error: String)

object ErrorResponse {
  implicit val codec: JsonCodec[ErrorResponse] = DeriveJsonCodec.gen[ErrorResponse]
}

// Сервис для работы с переводами
trait TransferService {
  def transferMoney(fromAccountId: UUID, toAccountId: UUID, amount: BigDecimal): ZIO[Any, String, UUID]
}

// Live реализация сервиса
class TransferServiceLive extends TransferService {
  override def transferMoney(fromAccountId: UUID, toAccountId: UUID, amount: BigDecimal): ZIO[Any, String, UUID] = {
    // Здесь должна быть реализация бизнес-логики перевода:
    // 1. Проверка существования счетов
    // 2. Проверка достаточности средств
    // 3. Выполнение перевода в транзакции
    // 4. Обновление балансов

    for {
      _ <- ZIO.when(amount <= 0)(ZIO.fail("Сумма перевода должна быть положительной"))
      _ <- ZIO.when(fromAccountId == toAccountId)(ZIO.fail("Нельзя переводить средства на тот же счет"))
      // Дополнительная бизнес-логика...
      transactionId <- ZIO.succeed(UUID.randomUUID())
    } yield transactionId
  }
}

object TransferServiceLive {
  val layer: ULayer[TransferService] = ZLayer.succeed(new TransferServiceLive)
}

// HTTP route
object TransferRoutes {

  def apply(): Http[TransferService, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case req @ Method.POST -> !! / "transfer" =>
        (for {
          transferRequest <- req.body.asString.flatMap { json =>
            ZIO.fromEither(json.fromJson[TransferRequest])
                .mapError(msg => s"Неверный формат запроса: $msg")
          }

          transactionId <- ZIO.serviceWithZIO[TransferService] { service =>
            service.transferMoney(
              transferRequest.fromAccountId,
              transferRequest.toAccountId,
              transferRequest.amount
            )
          }

          response = TransferResponse("Перевод выполнен успешно", transactionId)
        } yield Response.json(response.toJson))
          .catchAll { error =>
            ZIO.succeed(
              Response.json(ErrorResponse(error).toJson).status(Status.BadRequest)
            )
          }
    }
}

// Главное приложение
object MoneyTransferApp extends ZIOAppDefault {

  def run =
    Server.serve(TransferRoutes())
      .provide(
        Server.defaultWithPort(8080),
        TransferServiceLive.layer
      )
}