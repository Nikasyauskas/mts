name := "MTS"
scalaVersion := "3.3.7"

ThisBuild / evictionErrorLevel := sbt.util.Level.Warn

scalacOptions := Seq(
  "-deprecation"
)

lazy val root = (project in file("."))
  .settings(
    name := "mts",
    libraryDependencies ++=
      Dependencies.ZIO ++
      Dependencies.ZioConfig ++
        Seq(
          Dependencies.zioHttp,
          Dependencies.jwtScala,
          Dependencies.jbcrypt,
          Dependencies.zioQuill,
          Dependencies.postgres,
          Dependencies.zioLogging,
          Dependencies.zioJson,
          Dependencies.logback,
          Dependencies.ScalaTest % Test
        ) ++ Dependencies.testContainers,
    dependencyOverrides += "dev.zio" %% "zio-logging" % "2.1.15",
    assembly / logLevel := Level.Debug,
    assembly / assemblyJarName := s"$name-assembly.jar",
    Test / testFrameworks := Seq(
      new TestFramework("zio.test.sbt.ZTestFramework"),
      TestFrameworks.ScalaTest
    )
  )