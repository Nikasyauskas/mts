package ru.rdnn.api

import ru.rdnn.AppError
import ru.rdnn.AppError.AccountNotFound
import ru.rdnn.dbrepositories._
import zio._
import zio.http._
import zio.json._
import zio.test._

import java.io.PrintWriter
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

object JwtAuthAPISpec extends ZIOSpecDefault {

  private val seedBcryptHash = "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi"

  private val loginUser = User(
    id = UUID.fromString("e4224dc9-ac32-4682-a43c-d7cfc791af5b"),
    user_name = "Agent",
    email = "007@bank.local",
    password_hash = seedBcryptHash,
    phone = "+1",
    created_at = ZonedDateTime.parse("2020-01-01T00:00:00Z"),
    updated_at = ZonedDateTime.parse("2020-01-01T00:00:00Z"),
    is_active = true
  )

  private def dummyDs: ZLayer[Any, Nothing, DataSource] =
    ZLayer.succeed(new DataSource {
      override def getConnection = null
      override def getConnection(u: String, p: String) = null
      override def getLogWriter = null
      override def setLogWriter(out: PrintWriter): Unit = ()
      override def getLoginTimeout = 0
      override def setLoginTimeout(seconds: Int): Unit = ()
      override def getParentLogger = null
      override def unwrap[T](iface: Class[T]) = null.asInstanceOf[T]
      override def isWrapperFor(iface: Class[?]) = false
    })

  private val mockAccounts: AccountsRepository = new AccountsRepository {
    override def findAccountByAccountNumber(accountNumber: String) =
      ZIO.fail(AccountNotFound(accountNumber))
    override def insertAccount(account: Accounts): ZIO[DataSource, AppError, Unit] = ZIO.unit
    override def withdrawalAccount(account: Accounts, amount: Float): ZIO[DataSource, AppError, Unit] = ZIO.unit
    override def creditAccount(account: Accounts, amount: Float): ZIO[DataSource, AppError, Unit] = ZIO.unit
  }

  private val mockBalance: BalanceHistoryRepository = new BalanceHistoryRepository {
    override def insertNewBalance(newBalance: BalanceHistory): ZIO[DataSource, AppError, Unit] = ZIO.unit
    override def findBalanceByAccountNumbers(
      transactionRequest: TransferRequest
    ): ZIO[DataSource, AppError, (BalanceHistory, BalanceHistory)] =
      ZIO.dieMessage("not used in auth tests")
  }

  private val loginRepo = new UserRepository {
    override def findUserById(id: UUID): ZIO[DataSource, AppError, Option[User]] = ZIO.succeed(None)
    override def findUserByEmail(email: String): ZIO[DataSource, AppError, Option[User]] =
      ZIO.succeed(if (email == loginUser.email) Some(loginUser) else None)
    override def insertUser(user: User): ZIO[DataSource, AppError, Unit] = ZIO.unit
  }

  private def authLayers: ZLayer[Any, Nothing, DataSource with UserRepository with AccountsRepository with BalanceHistoryRepository] =
    dummyDs ++ ZLayer.succeed(loginRepo) ++ ZLayer.succeed(mockAccounts) ++ ZLayer.succeed(mockBalance)

  def spec = suite("JwtAuthAPI")(
    test("POST /auth/token with valid password returns 200 and Bearer token JSON") {
      val body = s"""{"email":"${loginUser.email}","password":"password"}"""
      val req  = Request.post(URL(Path.root / "auth" / "token"), Body.fromString(body))
      for {
        resp <- JwtAuthAPI.authRoutes.runZIO(req).provide(Scope.default ++ authLayers)
        text <- resp.body.asString
        parsed <- ZIO.fromEither(text.fromJson[JwtAuthAPI.AuthResponse])
      } yield assertTrue(resp.status == Status.Ok, parsed.token.nonEmpty)
    },
    test("POST /auth/token with wrong password returns 401") {
      val body = s"""{"email":"${loginUser.email}","password":"wrong"}"""
      val req  = Request.post(URL(Path.root / "auth" / "token"), Body.fromString(body))
      for {
        resp <- JwtAuthAPI.authRoutes.runZIO(req).provide(Scope.default ++ authLayers)
      } yield assertTrue(resp.status == Status.Unauthorized)
    },
    test("POST /auth/register rejects short password with 400") {
      val body = """{"email":"new@x.local","password":"1234567","userName":"Bob","phone":""}"""
      val req  = Request.post(URL(Path.root / "auth" / "register"), Body.fromString(body))
      for {
        resp <- JwtAuthAPI.authRoutes.runZIO(req).provide(Scope.default ++ authLayers)
      } yield assertTrue(resp.status == Status.BadRequest)
    }
  )
}
