import sbt._

object Dependencies {

  lazy val ZioVersion = "2.1.15"
  lazy val ZioConfigVersion = "4.0.2"
  lazy val ZIOHttpVersion = "3.3.3"
  lazy val zioQuillVersion = "4.8.5"
  lazy val zioLoggingVersion = "2.1.15"

  lazy val LiquibaseVersion = "3.4.2"
  lazy val PostgresVersion = "42.3.1"
  lazy val LogbackVersion = "1.2.3"
  lazy val ScalaTestVersion = "3.2.19"

  lazy val ZIO: Seq[ModuleID] = Seq(
    "dev.zio" %% "zio" % ZioVersion,
    "dev.zio" %% "zio-test" % ZioVersion  % Test,
    "dev.zio" %% "zio-test-sbt" % ZioVersion  % Test,
    "dev.zio" %% "zio-test-magnolia" % ZioVersion % Test
  )

  lazy val ZioConfig: Seq[ModuleID] = Seq(
    "dev.zio" %% "zio-config" % ZioConfigVersion,
    "dev.zio" %% "zio-config-magnolia" % ZioConfigVersion,
    "dev.zio" %% "zio-config-typesafe" % ZioConfigVersion
  )

  lazy val zioHttp = "dev.zio" %% "zio-http" % ZIOHttpVersion
  lazy val zioQuill = "io.getquill" %% "quill-jdbc-zio" % zioQuillVersion
  lazy val zioLogging = "dev.zio" %% "zio-logging" % zioLoggingVersion

  lazy val liquibase = "org.liquibase" % "liquibase-core" % LiquibaseVersion
  lazy val postgres = "org.postgresql" % "postgresql" % PostgresVersion
  lazy val logback = "ch.qos.logback"  %  "logback-classic" % LogbackVersion
  lazy val ScalaTest = "org.scalatest" %% "scalatest" % ScalaTestVersion

  lazy val  testContainers = Seq(
    "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.39.12"  % Test,
    "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.39.12"  % Test
  )
}
