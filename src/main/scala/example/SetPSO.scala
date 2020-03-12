package example

import cilib._
import scalaz.NonEmptyList
import scalaz.Scalaz._

object SetPSO {

  def apply[S](
      c1: Double,
      c2: Double,
      c3: Double,
      c4: Double,
      U: Set[Int]
  ): NonEmptyList[SetParticle] => SetParticle => Step[Int, SetParticle] =
    swarm =>
      x =>
        for {
          cog <- personalBest(x)
          soc <- globalBest(swarm)
          v <- velocity(c1, c2, c3, c4, x, soc, cog, U)
          p <- position(x, v)
          p2 <- evaluate(p)
          updated <- updatePersonalBest(p2)
        } yield updated

  private def personalBest(p: SetParticle): Step[Int, Set[Int]] =
    Step.point(p.state.best.pos.toSet)

  private def globalBest(xs: NonEmptyList[SetParticle]): Step[Int, Set[Int]] =
    Step.withCompare(o => {
      xs.map(_.state.best)
        .toList
        .reduceLeftOption((a, c) => Comparison.compare(a, c).apply(o))
        .getOrElse(sys.error("Impossible"))
        .pos
        .toSet
    })

  private def velocity(
      c1: Double,
      c2: Double,
      c3: Double,
      c4: Double,
      p: SetParticle,
      pb: Set[Int],
      gb: Set[Int],
      U: Set[Int]
  ) = {
    val pos = p.pos.pos.toSet
    val stdUniform = Dist.stdUniform.flatMap[Set[Operation]] _
    Step.pointR[Int, Set[Operation]] {
      for {
        cog <- stdUniform(r => ⊗(c1 * r, ⊖(pb, pos)))
        soc <- stdUniform(r => ⊗(c2 * r, ⊖(gb, pos)))
        add <- stdUniform(r => ⊙+(c3 * r, U, pos, pb, gb))
        remove <- stdUniform(r => ⊙−(c4 * r, pos, pb, gb))
      } yield ⊕(⊕(cog, soc), ⊕(add, remove))
    }
  }

  private def position(p: SetParticle, v: Set[Operation]): Step[Int, SetParticle] = {
    val pos = p.pos.pos.toSet
    val updated = v
      .foldLeft(pos) {
        case (set, Add(elem))    => set + elem
        case (set, Remove(elem)) => if (set.size == 1) set else set.filter(_ != elem)
      }
      .toList
      .toNel
      .getOrElse(sys.error("Impossible"))

    Step.point(Entity(p.state, Position(updated, p.pos.boundary)))
  }

  private def evaluate(p: SetParticle): Step[Int, SetParticle] =
    Step.eval[SetMem, Int](x => x)(p)

  private def updatePersonalBest(p: SetParticle): Step[Int, SetParticle] =
    Step
      .withCompare(Comparison.compare(p.pos, p.state.best))
      .map(x => Entity(SetMem(x), p.pos))

  private def ⊕(v1: Set[Operation], v2: Set[Operation]): Set[Operation] =
    v1.union(v2)

  private def ⊗(n: Double, v: Set[Operation]): RVar[Set[Operation]] =
    v.toList.toNel match {
      case Some(nel) =>
        RVar
          .sample((n * v.size).toInt, nel)
          .map(_.toSet)
          .getOrElse(Set())
      case None => RVar.point(Set[Operation]())
    }

  private def ⊖(x1: Set[Int], x2: Set[Int]): Set[Operation] =
    x1.diff(x2).map(Add) ++ x2.diff(x1).map(Remove)

  private def ⊙−(b: Double, x: Set[Int], pb: Set[Int], gb: Set[Int]) = {
    val S = x.union(pb).union(gb)
    N(b, S).flatMap(n => ⊗(n / S.size.toDouble, S.map(Remove)))
  }

  private def ⊙+(
      b: Double,
      U: Set[Int],
      p: Set[Int],
      pb: Set[Int],
      gb: Set[Int]
  ): RVar[Set[Operation]] = {
    val A = U.diff(p.union(pb).union(gb))
    A.toList.toNel match {
      case Some(nel) =>
        for {
          n <- N(b, A)
          shuffled <- RVar.shuffle(nel)
        } yield shuffled.toList.take(n).toSet.map(Add)
      case None => RVar.point(Set())
    }
  }

  private def N(b: Double, S: Set[Int]): RVar[Int] =
    Dist.stdUniform.map { r =>
      val II = if (r < b - b.toInt) 1 else 0
      math.min(S.size, b.toInt + II)
    }

}
