package ru.rdnn

import zio.Config
import zio.config.magnolia.deriveConfig

case class Conf(
  host: String,
  port: String
)

object AppConf {
  val conf: Config[Conf] = deriveConfig[Conf]
}
