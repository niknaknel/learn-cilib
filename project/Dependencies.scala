import sbt._

object Dependencies {
  val cilibVersion = "2.0.1"

  lazy val cilibCore = "net.cilib" %% "cilib-core" % cilibVersion
  lazy val cilibExec = "net.cilib" %% "cilib-exec" % cilibVersion
  lazy val cilibPSO = "net.cilib" %% "cilib-pso" % cilibVersion
  lazy val benchmarks = "net.cilib" %% "benchmarks" % "0.1.1"
}
