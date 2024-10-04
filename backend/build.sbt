// Project-level settings
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.14"

// Project definition
lazy val root = (project in file("."))
  .settings(
    name := "backend",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % "0.21.24",
      "org.http4s" %% "http4s-blaze-client" % "0.21.24",
      "org.http4s" %% "http4s-dsl" % "0.21.24",
      "org.http4s" %% "http4s-core" % "0.21.24",
      "org.typelevel" %% "cats-effect" % "2.5.3",
      "org.postgresql" % "postgresql" % "42.7.3",
      "ch.qos.logback" % "logback-classic" % "1.5.6",
      "org.slf4j" % "slf4j-api" % "2.0.12",
      "com.google.api-client" % "google-api-client" % "2.6.0",
      "com.google.http-client" % "google-http-client" % "1.44.1",
      "com.google.oauth-client" % "google-oauth-client" % "1.35.0",
      "com.google.apis" % "google-api-services-sheets" % "v4-rev20240917-2.0.0",
      "com.google.apis" % "google-api-services-drive" % "v3-rev20240914-2.0.0",
      "org.tpolecat" %% "doobie-core" % "0.13.4",
      "org.tpolecat" %% "doobie-postgres" % "0.13.4",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.17.0",
      "org.http4s" %% "http4s-server" % "0.21.24",
      "com.typesafe.play" %% "play-json" % "2.10.5"
    )
  )