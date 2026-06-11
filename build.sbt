import scala.io.Source
import java.io.File

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.3"

lazy val root = (project in file("."))
  .settings(
    name := "vmi"
  )

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.client3" %% "core" % "3.9.7",
  "com.lihaoyi"                    %% "upickle" % "3.3.1",
  "org.kohsuke" % "github-api" % "1.326",
  "io.github.java-diff-utils" % "java-diff-utils" % "4.15",
  "org.eclipse.jdt" % "org.eclipse.jdt.core" % "3.39.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback"              % "logback-classic" % "1.5.6"
)

fork := true

// Pure sbt configuration mapping to read .env
lazy val loadEnvVars = taskKey[Map[String, String]]("Loads local .env file tokens")

ThisBuild / loadEnvVars := {
  val envFile = new File(".env")
  if (envFile.exists()) {
    Source.fromFile(envFile).getLines()
      .map(_.trim)
      .filterNot(line => line.isEmpty || line.startsWith("#"))
      .flatMap { line =>
        line.split("=", 2) match {
          case Array(k, v) => Some(k.trim -> v.trim)
          case _           => None
        }
      }.toMap
  } else Map.empty
}

// Dynamically inject the parsed map into your application's runtime
run / envVars ++= (ThisBuild / loadEnvVars).value
Test / envVars ++= (ThisBuild / loadEnvVars).value