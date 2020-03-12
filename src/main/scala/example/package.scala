import cilib.{Entity, Input, Step}
import scalaz.NonEmptyList
import scalaz.Scalaz._

package object example {

  type SetParticle = Entity[SetMem, Int]
  type Swarm = NonEmptyList[SetParticle]
  type Alg = Swarm => SetParticle => Step[Double, SetParticle]


  implicit val setInput = new Input[Set] {
    override def toInput[A](a: NonEmptyList[A]): Set[A] = a.toSet
  }

}
