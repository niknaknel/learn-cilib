package example

import cilib._
import cilib.exec.Runner.measure
import cilib.exec.{Measurement, Output}
import scalaz.Scalaz._
import scalaz._

final case class Results(min: Double, max: Double, average: Double, bestPos: NonEmptyList[Int]) extends Output

object Results {

  def toCSVLine(measurement: Measurement[Results]): String = {
    val gbPos = measurement.data.bestPos.toList.mkString(" ")
//    println(s"\tGlobal Best: $gbPos")

    List(
      measurement.alg,
      measurement.prob,
      measurement.seed.toString,
      measurement.iteration.toString,
      measurement.data.min.toString,
      measurement.data.max.toString,
      measurement.data.average.toString
    ).mkString(",")
  }

}

object PerformanceMeasures {

  private val feasibleOptic = Lenses._singleFitness[Int].composePrism(Lenses._feasible)

  def fitness(x: SetParticle, default: Double): Double =
    feasibleOptic.getOption(x.pos).getOrElse(default)

  def personalBestFitness(x: SetParticle, default: Double): Double =
    feasibleOptic.getOption(x.state.best).getOrElse(default)

  def maximum(fitnessValues: NonEmptyList[Double]): Double =
    fitnessValues.maximum1

  def minimum(fitnessValues: NonEmptyList[Double]): Double =
    fitnessValues.minimum1

  def average(fitnessValues: NonEmptyList[Double]): Double = {
    val sum = fitnessValues.foldl(0.0)(sum => x => sum + x)
    sum / fitnessValues.length.toDouble
  }

  private def selectGlobalBest(swarm: NonEmptyList[SetParticle]) =
    swarm.toList
      .map(x => x.state.best)
      .reduceLeftOption((a, c) => Comparison.compare(a, c).apply(Comparison.dominance(Min)))
      .getOrElse(sys.error("Impossible"))

  def swarmPerformance =
    measure[NonEmptyList, SetParticle, Results](swarm => {
      val fitnessValues = swarm.map(x => PerformanceMeasures.fitness(x, Double.PositiveInfinity))
      val min = PerformanceMeasures.minimum(fitnessValues)
      val max = PerformanceMeasures.maximum(fitnessValues)
      val average = PerformanceMeasures.average(fitnessValues)
      val gb = selectGlobalBest(swarm).pos

      Results(min, max, average, gb)
    })

}
