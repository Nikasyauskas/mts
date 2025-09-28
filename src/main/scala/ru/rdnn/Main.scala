package ru.rdnn

import io.getquill.SnakeCase
import io.getquill.context.ZioJdbc.QIO
import io.getquill.jdbczio.Quill
import zio._
import zio.config.typesafe.TypesafeConfigProvider
import ru.rdnn.configuration.Configuration
import java.sql.SQLException
import javax.sql.DataSource

case class Company(name: String)

class DataService(quill: Quill.Postgres[SnakeCase]) {

  val ctx = db.Ctx
  import ctx._

  val companySchema = quote {
    querySchema[Company]("""aeroflot.company""")
  }

  def getCompanies: QIO[List[Company]] = ctx.run(companySchema)
}
object DataService {
  def Companies: ZIO[DataService with DataSource, SQLException, List[Company]] =
    ZIO.serviceWithZIO[DataService](_.getCompanies)

  val live = ZLayer.fromFunction(new DataService(_))
}


object Main extends ZIOAppDefault {

  override val bootstrap: ULayer[Unit] =
    Runtime.setConfigProvider(
        TypesafeConfigProvider
          .fromResourcePath()
      ) ++ Logger.liveDefaultLogger

  private def app = for {
    conf <- Configuration.config
    _ <- ZIO.logInfo(s"user name is: ${conf.database.username}")
    companies <- DataService.Companies.mapError(_.asInstanceOf[Exception])
    _ <- ZIO.logInfo(s"Companies: ${companies.mkString(", ")}")
  } yield ()

  override def run: ZIO[Any, Exception, Unit] = app
    .provide(
      DataService.live,
      Quill.Postgres.fromNamingStrategy(SnakeCase),
      Quill.DataSource.fromPrefix("database").mapError(_.asInstanceOf[Exception])
    )
}
