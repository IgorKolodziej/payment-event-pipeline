import org.scalajs.linker.interface.ModuleKind

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.team"

// Shared dataset contract (DTOs + Circe codecs) cross-compiled to JVM (backend exporter)
// and JS (Laminar dashboard), so the BE/FE contract has a single, stable definition.
lazy val contract = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("contract"))
  .settings(
    name := "dashboard-contract",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % "0.14.15",
      "io.circe" %%% "circe-generic" % "0.14.15",
      "io.circe" %%% "circe-parser" % "0.14.15"
    )
  )

lazy val contractJVM = contract.jvm
lazy val contractJS = contract.js

lazy val root = (project in file("."))
  .dependsOn(contractJVM)
  .settings(
    name := "payment-event-pipeline",
    Compile / run / fork := true,
    Compile / run / mainClass := Some("com.team.pipeline.Main"),
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

// Scala.js + Laminar dashboard. `fastLinkJS` writes `main.js` into `dashboard/assets/`, which
// `dashboard/index.html` loads, so the page can be served statically with no Node build step.
lazy val dashboard = (project in file("dashboard"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(contractJS)
  .settings(
    name := "dashboard",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.NoModule) },
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory := baseDirectory.value / "assets",
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory := baseDirectory.value / "assets",
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "com.raquo" %%% "laminar" % "17.2.1",
      "io.circe" %%% "circe-parser" % "0.14.15"
    )
  )
