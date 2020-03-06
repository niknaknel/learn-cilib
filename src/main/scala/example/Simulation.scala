package example

import cilib._
import cilib.exec._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import scalaz.NonEmptyList
import scalaz.concurrent.Task
import scalaz.stream.Process

object Simulation {

  // A helper function for creating static simulations
  def createSimulation(
                        cmp: Comparison,
                        eval: RVar[NonEmptyList[Double] => Objective[Double]],
                        problemName: String Refined NonEmpty,
                        algorithm: Alg,
                        algorithmName: String Refined NonEmpty,
                        swarm: RVar[Swarm],
                        seed: Long): Process[Task, Progress[Swarm]] =
    Runner.foldStep[NonEmptyList, Double, Particle](
      Environment(cmp, eval),
      RNG.init(seed),
      swarm,
      Algorithm(algorithmName, Iteration.sync(algorithm)),
      Runner.staticProblem(problemName, eval, RNG.init(seed)),
      x => RVar.point(x)
    )

}