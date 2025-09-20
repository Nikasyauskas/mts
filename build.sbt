name := "MTS"
scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(
    name := "mts",
    libraryDependencies ++=
      Dependencies.ZIO ++
      Dependencies.ZioConfig ++ Seq(
        Dependencies.zioHttp,
        Dependencies.zioQuill,
        Dependencies.liquibase,
        Dependencies.postgres,
        Dependencies.zioLogging
      )
  )