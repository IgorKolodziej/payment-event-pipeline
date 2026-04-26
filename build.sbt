ThisBuild / scalaVersion := "3.3.7"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.team"
Compile / run / fork := true

lazy val root = (project in file("."))
  .settings(
    name := "payment-event-pipeline",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.13.0",
      "org.typelevel" %% "cats-effect" % "3.7.0",
      "co.fs2" %% "fs2-core" % "3.13.0",
      "co.fs2" %% "fs2-io" % "3.13.0",
      "com.github.fd4s" %% "fs2-kafka" % "3.9.1",
      "io.circe" %% "circe-core" % "0.14.15",
      "io.circe" %% "circe-parser" % "0.14.15",
      "io.circe" %% "circe-generic" % "0.14.15",
      "com.typesafe" % "config" % "1.4.3",
      "org.tpolecat" %% "doobie-core" % "1.0.0-RC11",
      "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC11",
      "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC11",
      "org.mongodb" % "mongodb-driver-sync" % "5.6.4",
      "org.scalameta" %% "munit" % "1.2.4" % Test,
      "org.typelevel" %% "munit-cats-effect" % "2.2.0" % Test
    )
  )
