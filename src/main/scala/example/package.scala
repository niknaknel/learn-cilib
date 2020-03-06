import cilib.{Entity, Mem, Step}
import scalaz.NonEmptyList

package object example {
  type Particle = Entity[Mem[Double], Double]
  type Swarm = NonEmptyList[Particle]
  type Alg = Swarm => Particle => Step[Double, Particle]
}
