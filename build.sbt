import Dependencies._

ThisBuild / scalaVersion     := "2.12.10"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "learn-cilib",
    libraryDependencies += cilibCore,
    libraryDependencies += cilibExec,
    libraryDependencies += cilibPSO
  )
