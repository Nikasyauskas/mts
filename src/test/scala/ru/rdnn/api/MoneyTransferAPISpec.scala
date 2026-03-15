package ru.rdnn.api

import ru.rdnn.AppError
import ru.rdnn.AppError.InsufficientBalance
import ru.rdnn.DataBaseService
import ru.rdnn.dbrepositories.TransferRequest
import zio.*
import zio.http.*
import zio.test.*

import javax.sql.DataSource
import java.io.*

object MoneyTransferAPISpec extends ZIOSpecDefault {

  private def dummyDataSource: ZLayer[Any, Nothing, DataSource] =
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

  private def mockDbService(result: ZIO[Any, AppError, Unit]): ZLayer[DataSource, Nothing, DataBaseService] =
    ZLayer.succeed(new DataBaseService {
      override def updateAccounts(transferRequest: TransferRequest) = result
      override def commitTransaction(transferRequest: TransferRequest) = result
      override def updateBalanceHistory(transferRequest: TransferRequest) = result
      override def provideTransaction(transferRequest: TransferRequest) = result
    })

  private def runPostTransfer(body: String): ZIO[Scope with DataSource with DataBaseService, Nothing, Response] = {
    val path = Path.root / "transfer" / "account-number"
    val req = Request.post(URL(path), Body.fromString(body))
    MoneyTransferAPI.api.runZIO(req)
  }

  private def routeLayer(success: Boolean): ZLayer[Any, Nothing, DataSource with DataBaseService] = {
    val result = if (success) ZIO.unit else ZIO.fail(InsufficientBalance(100f, 50f))
    dummyDataSource ++ (dummyDataSource >>> mockDbService(result))
  }

  def spec = suite("MoneyTransferAPI")(
    test("POST /transfer/account-number with invalid JSON returns 400") {
      val body = """{"invalid": true}"""
      for {
        resp <- runPostTransfer(body).provide(Scope.default ++ routeLayer(success = true))
      } yield assertTrue(resp.status == Status.BadRequest)
    },
    test("POST /transfer/account-number with valid JSON and failing service returns 400 for InsufficientBalance") {
      val body = """{"userId":"u1","fromAccount":"acc1","toAccount":"acc2","amount":100.0}"""
      for {
        resp <- runPostTransfer(body).provide(Scope.default ++ routeLayer(success = false))
      } yield assertTrue(resp.status == Status.BadRequest)
    },
    test("POST /transfer/account-number with valid JSON and success returns 200") {
      val body = """{"userId":"u1","fromAccount":"acc1","toAccount":"acc2","amount":50.0}"""
      for {
        resp <- runPostTransfer(body).provide(Scope.default ++ routeLayer(success = true))
      } yield assertTrue(resp.status == Status.Ok)
    }
  )
}
