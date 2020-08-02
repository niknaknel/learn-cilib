//package example
//
//import java.io.File
//
//import scalaz.effect.SafeApp
//import breeze.linalg._
//import breeze.numerics.{abs, round}
//import breeze.util.JavaArrayOps.{dmDToArray2, dvDToArray}
//import cilib._
//import cilib.exec._
//import eu.timepit.refined.api.Refined
//import eu.timepit.refined.auto._
//import eu.timepit.refined.collection.NonEmpty
//import eu.timepit.refined.numeric._
//import scalaz.NonEmptyList
//import scalaz.concurrent.Task
//import scalaz.effect.IO.putStrLn
//import scalaz.effect._
//import scalaz.stream.{Process, _}
//
//object Main extends SafeApp {
//
//    def test(fname: String Refined NonEmpty): Unit = {
//        val df = csvread(file = new File(fname), skipLines = 1, separator = ',')
//
////        println(dmDToArray2(df).deep.mkString("\n"))
//
//        // train test split
//        val temp = DenseMatrix(shuffle((0 until df.rows).map(i => df(i, ::).t)):_*)
//        val X = scaleData(temp(::, 0 to -2), 0.0, 1.0)
//        val y = convert(temp(::, -1), Double)
//
////        println(dmDToArray2(temp).deep.mkString("\n"))
////        println(dvDToArray(y).mkString(", "))
//
//
//        val split = round(0.6 * X.rows).toInt
//
//        val trainX = X(0 until split, ::)
//        val trainY = y(0 until split)
//        val testX = X(split to -1, ::)
//        val testY = y(split to -1)
//
//        val svm = new SVM()
//        svm.train(trainX, trainY, fname)
//
//        // training accuracy
//        val yPred = svm.predict(trainX)
//        println(dvDToArray(trainY).mkString(", "))
//        println(dvDToArray(yPred).mkString(", "))
//        svm.confusionMatrix(trainY, yPred)
//    }
//
//    def scaleData(X: DenseMatrix[Double], floor: Double, ceiling: Double): DenseMatrix[Double] = {
//        val maxX = max(X)
//        val minX = min(X)
//
//        val offset = floor - minX
//        val div = maxX / ceiling
//
//        val scaled = DenseMatrix.tabulate[Double](X.rows, X.cols)((i, j) => {
//            (offset + X(i, j)) / div
//        })
//
//        scaled
//    }
//
//    test("data/plane_3x+2.csv")
//    // test("data/iris.csv")
//}
//
//class SVM {
//
//    // SetPSO params
//    final val SEED: Int = 123
//    final val nParticles: Int Refined Positive = 20
//    final val iterations: Int = 100
//    final val independentRuns: Int = 5
//    final val cores = 8
//    final val outputFile = "results.csv"
//
//    // fitness params
//    final val error: Double = 1e-6
//    final val lambda: DenseVector[Double] = DenseVector(0.25, 0.25, 0.25, 0.25)
//
//    var w: DenseVector[Double] = new DenseVector[Double](1)
//    var b: Double = 0.0
//    var SVs: DenseMatrix[Double] = new DenseMatrix[Double](1, 1)
//
//    // Option[NonEmptyList[Int]]
//
//    def train(trainSet: DenseMatrix[Double], trainLabels: DenseVector[Double], testName: String Refined NonEmpty): Unit = {
//        // 1) Create set of indices
//        val U: Set[Int] = Set(0 until trainSet.rows: _*)
//
//        // 2) Create ACD optimiser
//        val acd = new ACD(trainSet, trainLabels)
//
//        // 3) Define objective function
//        def f: Set[Int] => Double = Xp => {
//            val nonSV = Set((0 until trainSet.rows).filter(i => !Xp.contains(i)): _*)
//
//            // find optimal hyperplane equation
//            val w = acd.findCoefficients(Xp)
//
//            // calculate fitness terms
//            val normNumSV = Xp.size/trainSet.rows.toDouble
//            val normw = norm(w(1 to -1))
//            val eqCV = acd.violations(Xp, w, i => abs(i) > error)
//            val ineqCV = acd.violations(nonSV, w, i => i < 0)
//
//            // λ1|X'| + λ2||w|| + λ3(% eq violations) + λ4(% ineq violations)
//            lambda(0) * normNumSV + lambda(1) * normw + lambda(2) * eqCV + lambda(3) * ineqCV
//        }
//
//        // 4) create environment (eval, cmp)
//        val eval = Eval.unconstrained[Set, Int](f).eval
//        val cmp = Comparison.dominance(Min)
//        val env = Environment(cmp, eval)
//
//        // 5) set up PSO
//        val setPSO = SetPSO(0.729844, 1.496180, 1.496180, 1.94561, U)
//        val algName: String Refined NonEmpty = "Set PSO"
//
//        val simulation =
//            SetParticle.createSwarm(U, nParticles).map { swarm =>
//                Runner.foldStep[NonEmptyList, Int, SetParticle](
//                    env,
//                    RNG.init(SEED),
//                    swarm,
//                    Algorithm("Set PSO", Iteration.sync(setPSO)),
//                    Runner.staticProblem(testName, eval, RNG.init(SEED)),
//                    x => RVar.point(x)
//                )
//            }
//
//        val measured: Process[Task, Measurement[Results]] =
//            simulation.map(_.take(iterations).pipe(PerformanceMeasures.swarmPerformance)).get
//
//        // TODO: try to multithread and show iteration output somehow!
//        def executeAndSaveResults: Process[Task, NonEmptyList[Int]] =
//
//        // This zips the measured simulation process with the file writing process
//        // i.e. You have (Process1, Process2)
//            measured.zipWith(ProjectIO.csvSink(outputFile, Results.toCSVLine))(
//                // Here we are saying when we run our processes, (Process1, Process2), what should we do with their results
//                // The first returns the Measurement[Result] for the iteration, and the second always returns the function to write to a file
//                (measurement, writeToFile) => {
//                    // Write to the given file now
//                    writeToFile(measurement).unsafePerformSync
//                    // But always return the best position
//                    measurement.data.bestPos
//                }
//            )
//
//        // 6) Run SetPSO
//        val runc =
//            for {
//                _ <- putStrLn("--> Description")
//                _ <- putStrLn(s"\tNumber of Independent Runs: ${independentRuns}")
//                _ <- putStrLn(s"\tBenchmark: ${testName.value}")
//                _ <- putStrLn(s"\tU: ${U}")
//                _ <- putStrLn(s"\tAlgorithm: ${algName.value}")
//                _ <- putStrLn(s"\tSwarm Size: ${nParticles.value}")
//                _ <- putStrLn(s"\tIterations: ${iterations}")
//                _ <- putStrLn(s"\tOutput File: ${outputFile}")
//                _ <- putStrLn(s"\tCores: ${cores}")
//                _ <- putStrLn("--> Executing")
//                start <- IO(System.currentTimeMillis())
//                result <- IO(executeAndSaveResults.runLast.unsafePerformSync)
//                finish <- IO(System.currentTimeMillis())
//                _ <- putStrLn("--> Complete")
//                _ <- putStrLn(s"\tDuration: ${Utilities.seconds(finish - start)}s")
//            } yield result
//
//        val finSet = Set(runc.unsafePerformIO().get.stream.toArray: _*)
//        val finW = acd.findCoefficients(finSet)
//
//        // set plane equation
//        w = finW(1 to -1)
//        b = finW(0)
//        println("w = (" + dvDToArray(w).mkString(", ") + ")")
//        println("b = " + b)
//
//        // set SVs
//        SVs = trainSet(finSet.toIndexedSeq, ::).toDenseMatrix
//    }
//
//    def predict(X: DenseMatrix[Double]): DenseVector[Double] = {
//        val yPred = DenseVector.tabulate[Double](X.rows)(i => if (b + w.dot(X(i, ::).t) > 0) 1.0 else 0.0)
//        yPred
//    }
//
//    def confusionMatrix(yExpected: DenseVector[Double], yPredicted: DenseVector[Double]): Unit = {
//        val tpCount = (0 until yExpected.length).count(i => yExpected(i) == 1.0 && yPredicted(i) == 1.0)
//        val fpCount = (0 until yExpected.length).count(i => yExpected(i) == 0.0 && yPredicted(i) == 1.0)
//        val tnCount = (0 until yExpected.length).count(i => yExpected(i) == 0.0 && yPredicted(i) == 0.0)
//        val fnCount = (0 until yExpected.length).count(i => yExpected(i) == 1.0 && yPredicted(i) == 0.0)
//
//        println("-----------------------------------")
//        println("        Confusion Matrix:\n-----------------------------------")
//        println("%28s".format("Actual"))
//        println("%14s%8s%8s".format(" ", "1", "0"))
//        println("%15s%20s".format("", "--------------------"))
//        println("Predicted %4s|%8s%8s".format("1", tpCount, fpCount))
//        println("%14s|%8s%8s".format("0", fnCount, tnCount))
//
//    }
//}
