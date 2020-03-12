package example

import cilib._
import cilib.exec._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric._
import scalaz.NonEmptyList
import scalaz.concurrent.Task
import scalaz.effect.IO.putStrLn
import scalaz.effect._
import scalaz.stream.{Process, _}

object Main extends SafeApp {

  // General config
  val iterations = 1000
  val independentRuns = 5
  val cores = 8
  val outputFile = "results.csv"
  val U = Set(2, 4, 6, 8, 10, 12, 13, 45, 99)
  val numberOfParticles: Int Refined Positive = 20

  // Algorithm
  val setPSO = SetPSO(0.729844, 1.496180, 1.496180, 1.94561, U)
  val algName: String Refined NonEmpty = "Set PSO"

  // Just a simple benchmark
  val benchmark: Set[Int] => Double = set => set.filter(_ % 2 == 0).sum
  val benchmarkName: String Refined NonEmpty = "Sum Even"
  val eval = Eval.unconstrained[Set, Int](benchmark).eval
  val cmp = Comparison.dominance(Max)
  val env = Environment(cmp, eval)

  val simulations =
    SetParticle.createSwarm(U, numberOfParticles) match {
      case Some(swarm) =>
        (for (x <- 1 to independentRuns) yield x).toList.map { run =>
          val seed = run.toLong
          Runner.foldStep[NonEmptyList, Int, SetParticle](
            env,
            RNG.init(seed),
            swarm,
            Algorithm("Set PSO", Iteration.sync(setPSO)),
            Runner.staticProblem(benchmarkName, eval, RNG.init(seed)),
            x => RVar.point(x)
          )
        }
      case None => List()
    }

  val measured: Process[Task, Process[Task, Measurement[Results]]] =
    Process.emitAll(simulations.map(_.take(iterations).pipe(PerformanceMeasures.swarmPerformance)))

  def executeAndSaveResults: Process[Task, Unit] =
    merge
      .mergeN(cores)(measured)
      .to(ProjectIO.csvSink(outputFile, Results.toCSVLine))

  override val runc: IO[Unit] =
    for {
      _ <- putStrLn("--> Description")
      _ <- putStrLn(s"\tNumber of Independent Runs: ${independentRuns}")
      _ <- putStrLn(s"\tBenchmark: ${benchmarkName.value}")
      _ <- putStrLn(s"\tU: ${U}")
      _ <- putStrLn(s"\tAlgorithm: ${algName.value}")
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
