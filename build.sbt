name := "githubrank"

version := "0.1"

scalaVersion := "2.13.8"

val AkkaVersion = "2.6.18"
val AkkaHttpVersion = "10.2.8"
val scalaTestVersion = "3.2.9"

libraryDependencies ++= Seq(
    // akka actor
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
    // akka streams
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
    // akka http
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion,
    // testing
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion,
    "org.scalatest" %% "scalatest" % scalaTestVersion,
    // logging
    "ch.qos.logback" % "logback-classic" % "1.2.10",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"
)