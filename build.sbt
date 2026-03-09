name := "MTS"
scalaVersion := "3.3.7"

ThisBuild / evictionErrorLevel := sbt.util.Level.Warn

lazy val root = (project in file("."))
  .settings(
    name := "mts",
    libraryDependencies ++=
      Dependencies.ZIO ++
      Dependencies.ZioConfig ++
        Seq(
          Dependencies.zioHttp,
          Dependencies.zioQuill,
          Dependencies.liquibase,
          Dependencies.postgres,
          Dependencies.zioLogging,
          Dependencies.zioJson,
          Dependencies.logback
      ),
    dependencyOverrides += "dev.zio" %% "zio-logging" % "2.1.15"
  )