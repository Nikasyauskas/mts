package ru.rdnn

import ru.rdnn.AppError._
import ru.rdnn.dbrepositories._
import zio._
import zio.test._

import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

object DataBaseServiceSpec extends ZIOSpecDefault {

  private val accountFrom = Accounts(
    UUID.randomUUID(),
    UUID.randomUUID(),
    "acc1",
    "RUB",
    100f,
    ZonedDateTime.now(),
    ZonedDateTime.now(),
    is_active = true
  )
  private val accountTo = accountFrom.copy(id = UUID.randomUUID(), account_number = "acc2", balance = 50f)

  private def mkRequest(from: String = "acc1", to: String = "acc2", amount: Float = 50f) =
    TransferRequest("user1", from, to, amount)

  private def mockAccountsRepo(
    from: Option[Accounts] = Some(accountFrom),
    to: Option[Accounts] = Some(accountTo),
    withdrawalFails: Boolean = false,
    creditFails: Boolean = false
  ): ZLayer[DataSource, Nothing, AccountsRepository] =
    ZLayer.succeed(new AccountsRepository {
      override def findAccountByAccountNumber(accountNumber: String): ZIO[DataSource, AppError, Accounts] =
        accountNumber match {
          case "acc1" => ZIO.fromOption(from).orElseFail(AccountNotFound(accountNumber))
          case "acc2" => ZIO.fromOption(to).orElseFail(AccountNotFound(accountNumber))
          case _      => ZIO.fail(AccountNotFound(accountNumber))
        }
      override def insertAccount(account: Accounts): ZIO[DataSource, AppError, Unit] = ZIO.unit
      override def withdrawalAccount(account: Accounts, amount: Float): ZIO[DataSource, AppError, Unit] =
        if (withdrawalFails) ZIO.fail(DbError(new RuntimeException("db"))) else ZIO.unit
      override def creditAccount(account: Accounts, amount: Float): ZIO[DataSource, AppError, Unit] =
        if (creditFails) ZIO.fail(DbError(new RuntimeException("db"))) else ZIO.unit
    })

  private val mockTransactionsRepo: ZLayer[DataSource, Nothing, TransactionsRepository] =
    ZLayer.succeed(new TransactionsRepository {
      override def insertTransaction(transaction: Transactions): ZIO[DataSource, Throwable, Unit] = ZIO.unit
    })

  private val bhFrom = BalanceHistory(UUID.randomUUID(), "acc1", 0f, 100f, 100f, ZonedDateTime.now())
  private val bhTo   = BalanceHistory(UUID.randomUUID(), "acc2", 0f, 50f, 50f, ZonedDateTime.now())

  private val mockBalanceHistoryRepo: ZLayer[DataSource, Nothing, BalanceHistoryRepository] =
    ZLayer.succeed(new BalanceHistoryRepository {
      override def insertNewBalance(newBalance: BalanceHistory): ZIO[DataSource, AppError, Unit] = ZIO.unit
      override def findBalanceByAccountNumbers(
        transactionRequest: TransferRequest
      ): ZIO[DataSource, AppError, (BalanceHistory, BalanceHistory)] =
        ZIO.succeed((bhFrom, bhTo))
    })

  private val mockUserRepo: ZLayer[DataSource, Nothing, UserRepository] =
    ZLayer.succeed(new UserRepository {
      override def findUserById(id: UUID): ZIO[DataSource, AppError, Option[User]] = ZIO.succeed(None)
      override def findUserByEmail(email: String): ZIO[DataSource, AppError, Option[User]] = ZIO.succeed(None)
      override def insertUser(user: User): ZIO[DataSource, AppError, Unit] = ZIO.unit
    })

  private def dummyDataSource: ZLayer[Any, Nothing, DataSource] =
    ZLayer.succeed(new javax.sql.DataSource {
      override def getConnection = null
      override def getConnection(u: String, p: String) = null
      override def getLogWriter = null
      override def setLogWriter(out: java.io.PrintWriter): Unit = ()
      override def getLoginTimeout = 0
      override def setLoginTimeout(seconds: Int): Unit = ()
      override def getParentLogger = null
      override def unwrap[T](iface: Class[T]) = null.asInstanceOf[T]
      override def isWrapperFor(iface: Class[?]) = false
    })

  private def env(
    accountsRepo: ZLayer[DataSource, Nothing, AccountsRepository] = mockAccountsRepo()
  ): ZLayer[Any, Nothing, DataSource with DataBaseService] =
    dummyDataSource ++ (dummyDataSource >>> (accountsRepo ++ mockTransactionsRepo ++ mockBalanceHistoryRepo ++ mockUserRepo) >>> DataBaseService.live)

  def spec = suite("DataBaseService")(
    test("updateAccounts fails with InsufficientBalance when balance < amount") {
      val req = mkRequest(amount = 200f)
      val prog = DataBaseService.updateAccounts(req)
      assertZIO(prog.exit)(Assertion.failsWithA[InsufficientBalance])
    }.provide(env()),
    test("updateAccounts fails with NonPositiveAmount when amount <= 0") {
      val req = mkRequest(amount = 0f)
      val prog = DataBaseService.updateAccounts(req)
      assertZIO(prog.exit)(Assertion.failsWithA[NonPositiveAmount])
    }.provide(env()),
    test("updateAccounts fails with NonPositiveAmount for negative amount") {
      val req = mkRequest(amount = -1f)
      val prog = DataBaseService.updateAccounts(req)
      assertZIO(prog.exit)(Assertion.failsWithA[NonPositiveAmount])
    }.provide(env()),
    test("updateAccounts succeeds when balance >= amount and amount > 0") {
      val req = mkRequest(amount = 50f)
      val prog = DataBaseService.updateAccounts(req)
      assertZIO(prog)(Assertion.anything)
    }.provide(env()),
    test("updateAccounts fails with AccountNotFound when from-account missing") {
      val req = mkRequest(from = "missing", to = "acc2", amount = 10f)
      val prog = DataBaseService.updateAccounts(req)
      assertZIO(prog.exit)(Assertion.failsWithA[AccountNotFound])
    }.provide(env(mockAccountsRepo(from = None)))
  )
}
