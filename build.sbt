import Dependencies._

ThisBuild / scalaVersion     := "2.12.10"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

//libraryDependencies ++= Seq(
//    "org.scalanlp" %% "breeze" % "1.0",
//    "org.scalanlp" %% "breeze-natives" % "1.0",
//    "net.cilib" %% "cilib-core" % "2.0.1",
//    "net.cilib" %% "cilib-exec" % "2.0.1",
//    "net.cilib" %% "cilib-pso" % "2.0.1"
//    )

val excBreezeSpire = ExclusionRule(organization="*", name="spire_2.12")

lazy val root = (project in file("."))
  .settings(
      name := "learn-cilib",
      libraryDependencies += cilibCore,
      libraryDependencies += cilibExec,
      libraryDependencies += cilibPSO,
      libraryDependencies += benchmarks,
	  libraryDependencies += "org.scalanlp" %% "breeze" % "1.0" excludeAll(excBreezeSpire),
	  libraryDependencies += "org.scalanlp" %% "breeze-natives" % "1.0" excludeAll(excBreezeSpire)
  )
