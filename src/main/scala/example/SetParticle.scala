package example

import cilib.{Entity, _}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import scalaz.NonEmptyList
import scalaz.Scalaz._
import spire.implicits._
import spire.math.Interval

sealed abstract class Operation
final case class Add(value: Int) extends Operation
final case class Remove(value: Int) extends Operation

final case class SetMem(best: Position[Int])

object SetParticle {

  type SetParticle = Entity[SetMem, Int]

  def createSwarm(U: Set[Int], n: Int Refined Positive): Option[RVar[Swarm]] =
    U.toList.toNel.map { xs =>
      createParticle(xs)
        .replicateM(n.value)
        .map(
          _.toNel
            .getOrElse(sys.error("Impossible -> refinement requires n to be positive, i.e. n > 0"))
        )
    }

  private def createParticle(U: NonEmptyList[Int]): RVar[SetParticle] =
    for {
      shuffeld <- RVar.shuffle(U)
      n <- Dist.uniformInt(Interval(1, U.size)) // poisson?
      set = shuffeld.toList.take(n).toNel.getOrElse(sys.error("Impossible -> n >= 1"))
      position = Position(set, Interval(1.0, U.size.toDouble) ^ 30)
    } yield Entity(SetMem(position), position)

}
