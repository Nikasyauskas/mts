package ru.rdnn

import zio._
import zio.logging.LogFormat._
import zio.logging.{console, consoleLogger, ConsoleLoggerConfig, LogColor, LogFilter, LogFormat}

import java.time.LocalDateTime

object Logg {

  private val logFormat: LogFormat = timestamp.fixed(32).highlight(_ => LogColor.GREEN) |-|
    level |-| label("msg:", quoted(line)).highlight
  private val currentTimeStamp: String = LocalDateTime.now().toString

  val liveConsoleLogger: ULayer[Unit] = Runtime.removeDefaultLoggers >>> consoleLogger(ConsoleLoggerConfig.default)

  val liveCustomLogger: ULayer[Unit] =
    Runtime.removeDefaultLoggers >>> consoleLogger(ConsoleLoggerConfig(logFormat, LogFilter.logLevel(LogLevel.Trace)))
}
