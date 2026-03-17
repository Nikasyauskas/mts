package ru.rdnn

import com.zaxxer.hikari.HikariDataSource
import io.getquill._
import io.getquill.jdbczio.Quill
import io.getquill.util.LoadConfig
import zio.ZLayer

import javax.sql.DataSource


package object db {

  object Ctx extends PostgresZioJdbcContext(NamingStrategy(Escape, Literal))

  private def hikariDS: HikariDataSource = new JdbcContextConfig(LoadConfig("database")).dataSource

  val zioDS: ZLayer[Any, Throwable, DataSource] =
    ZLayer.succeed(hikariDS)
    
  val quillDS: ZLayer[Any, Throwable, DataSource] = 
    Quill.DataSource.fromPrefix("database")

}
