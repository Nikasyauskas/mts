package ru.rdnn

import zio.config.magnolia.deriveConfig
import zio.config.typesafe.TypesafeConfigProvider
import zio.{Config, _}

package object configuration {

  case class DatabaseConfig(
    jdbcUrl: String,
    username: String,
    password: String,
    driverClassName: String
  )

  case class Conf(
    database: DatabaseConfig
  )

  private val applicationConfig: Config[Conf] = deriveConfig[Conf]

  object Configuration {
    val config: IO[Config.Error, Conf] = TypesafeConfigProvider.fromResourcePath().load(applicationConfig)
  }
}
