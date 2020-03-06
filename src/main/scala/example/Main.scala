package example

import cilib._
import cilib.benchmarks.Benchmarks
import cilib.exec._
import cilib.pso.Defaults._
import cilib.pso._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric._
import scalaz.NonEmptyList
import scalaz.concurrent.Task
import scalaz.effect.IO.putStrLn
import scalaz.effect._
import scalaz.stream.{Process, _}
import spire.implicits._
import spire.math.Interval

object Main extends SafeApp {

  // General config
  val iterations = 1000
  val independentRuns = 5
  val cores = 8
  val outputFile = "results.csv"
  val numberOfParticles: Int Refined Positive = 20
  val bounds = Interval(-5.12, 5.12) ^ 30 // The bounds of our search space(s)

  // Our algorithm
  val cognitive = Guide.pbest[Mem[Double], Double]
  val social = Guide.gbest[Mem[Double]]
  val gbestPSO = gbest(0.729844, 1.496180, 1.496180, cognitive, social)
  val algorithmName: String Refined NonEmpty = "GBest PSO"
  val swarm = Position.createCollection(PSO.createParticle(x => Entity(Mem(x, x.zeroed), x)))(bounds, numberOfParticles)

  // Benchmark names and their functions
  val benchmarks: List[(String Refined NonEmpty, NonEmptyList[Double] => Double)] = List(
    ("Spherical", Benchmarks.spherical[NonEmptyList, Double] _),
    ("Absolute Value", Benchmarks.absoluteValue[NonEmptyList, Double] _),
    ("Ackley", Benchmarks.ackley[NonEmptyList, Double] _),
  )

  val simulations = (for (x <- 1 to independentRuns) yield x).toList.flatMap { run =>
    benchmarks.map { case (name, benchmark) =>
      val eval = Eval.unconstrained(benchmark).eval
      val cmp = Comparison.dominance(Min)
      Simulation
        .createSimulation(cmp, eval, name, gbestPSO, algorithmName, swarm, run)
      // For simplicity, we pass the independent run as the seed for the simulation
    }
  }

  val measured: Process[Task, Process[Task, Measurement[Results]]] =
    Process.emitAll(simulations.map(_.take(iterations).pipe(PerformanceMeasures.swarmPerformance)))

  def executeAndSaveResults: Process[Task, Unit] =
    merge
      .mergeN(cores)(measured)
      .to(ProjectIO.csvSink(outputFile, PerformanceMeasures.toCSVLine))

  val benchmarkNames = benchmarks.map(_._1.value).mkString(", ")

  override val runc: IO[Unit] =
    for {
      _ <- putStrLn("--> Description")
      _ <- putStrLn(s"\tNumber of Independent Runs: ${independentRuns}")
      _ <- putStrLn(s"\tBenchmarks: ${benchmarkNames}")
      _ <- putStrLn(s"\tBounds: ${bounds.head}")
      _ <- putStrLn(s"\tAlgorithm: ${algorithmName.value}")
      _ <- putStrLn(s"\tSwarm Size: ${numberOfParticles.value}")
      _ <- putStrLn(s"\tIterations: ${iterations}")
      _ <- putStrLn(s"\tOutput File: ${outputFile}")
      _ <- putStrLn(s"\tCores: ${cores}")
      _ <- putStrLn("--> Executing")
      start <- IO(System.currentTimeMillis())
      _ <- IO(executeAndSaveResults.run.unsafePerformSync)
      finish <- IO(System.currentTimeMillis())
      _ <- putStrLn("--> Complete")
      _ <- putStrLn(s"\tDuration: ${Utilities.seconds(finish - start)}s")
    } yield ()

}