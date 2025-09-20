package ru.rdnn

import zio._

object Main extends ZIOAppDefault {

  def app: UIO[Unit] = ZIO.log("Hello World!")

  override def run: UIO[Unit] = app
}