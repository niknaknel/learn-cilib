package example

import breeze.util.JavaArrayOps.dvDToArray
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
    val numberOfParticles: Int Refined Positive = 20

    // Just a simple benchmark
    //  val benchmark: Set[Int] => Double = set => set.filter(_ % 2 == 0).sum
    //  val benchmarkName: String Refined NonEmpty = "Sum Even"
    //  val eval = Eval.unconstrained[Set, Int](benchmark).eval
    //  val cmp = Comparison.dominance(Max)
    //  val env = Environment(cmp, eval)

    /**
      * Test SetPSO
      * @param targetSum
      * @return resulting optimal set
      */
    def runMinSumTest(targetSum:Int): Unit = {

        // Smallest number of elements which sum to a given int N
        val N: Double = targetSum.toDouble
        val benchmark: Set[Int] => Double = set => {
            if (set.sum == N) {
                set.size
            } else {
                Int.MaxValue
            }
        }

        // Set up environment
        val U = Set(2, 4, 6, 8, 10, 12, 13, 45, 99)
        val benchmarkName: String Refined NonEmpty = "Min Sum Equal"
        val eval = Eval.unconstrained[Set, Int](benchmark).eval
        val cmp = Comparison.dominance(Min)
        val env = Environment(cmp, eval)

        // Algorithm
        val setPSO = SetPSO(0.729844, 1.496180, 1.496180, 1.94561, U)
        val algName: String Refined NonEmpty = "Set PSO"

        // Some output
        println("--> Description")
        println(s"\tBenchmark: ${benchmarkName.value}")
        println(s"\tU: ${U}")
        println(s"\tAlgorithm: ${algName.value}")
        println(s"\tSwarm Size: ${numberOfParticles.value}")
        println(s"\tIterations: ${iterations}")
        println(s"\tOutput File: ${outputFile}")
        println(s"\tCores: ${cores}")

        val startTime = System.currentTimeMillis()

        for (run <- 1 to independentRuns) {
            val SEED = run.toLong

            // set up simulation
            val simulation =
                SetParticle.createSwarm(U, numberOfParticles).map { swarm =>
                    Runner.foldStep[NonEmptyList, Int, SetParticle](
                        env,
                        RNG.init(SEED),
                        swarm,
                        Algorithm("Set PSO", Iteration.sync(setPSO)),
                        Runner.staticProblem(benchmarkName, eval, RNG.init(SEED)),
                        x => RVar.point(x)
                    )
                }

            val measured: Process[Task, Measurement[Results]] =
                simulation.map(_.take(iterations).pipe(PerformanceMeasures.swarmPerformance)).get

            def executeAndSaveResults: Process[Task, NonEmptyList[Int]] =
                measured.zipWith(ProjectIO.csvSink(outputFile, Results.toCSVLine))(
                    (measurement, writeToFile) => {
                        writeToFile(measurement).unsafePerformSync
                        measurement.data.bestPos
                    }
                )

            // 6) Run SetPSO
            val runc =
                for {
                    result <- IO(executeAndSaveResults.runLast.unsafePerformSync)
                } yield result

            val res = Set(runc.unsafePerformIO().get.stream.toArray: _*)

            println(s"${run}) RESULT: " + res)
        }

        val endTime = System.currentTimeMillis()
        println("--> Complete")
        println(s"\tDuration: ${Utilities.seconds(endTime - startTime)}s")
        println("--------------------------\n")
    }

    runMinSumTest(20)
    runMinSumTest(24)
    runMinSumTest(45)
}
