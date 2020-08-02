package example

import breeze.linalg.{DenseMatrix, DenseVector, diag, eig, inv, norm, sum}
import breeze.numerics.{abs, cos, pow, round, sin, sqrt}
import breeze.stats.distributions.Uniform
import breeze.util.JavaArrayOps.{dmDToArray2, dvDToArray}

import scala.util.Random.shuffle
import scalaz.effect.SafeApp

import scala.math.Pi

class ACD(Xc:DenseMatrix[Double], Yc:DenseVector[Double]) {
//object ACD extends SafeApp {

    // ACD params
    final val maxIters:Int = 1000
    final val tolerance:Double = 1E-10
    final val kSucc:Double = 2.0
    final val kUnsucc:Double = 0.5
    final val wmin:Double = 0.0
    final val wmax:Double = 1.0
    final val lambda:DenseVector[Double] = DenseVector(0.33, 0.33, 0.33)
    final val error:Double = 1E-6
    final val cvProp:Double= 0.5 // proportion of nonSVs to use for viotion count. Currently same used for SetPSO

    val X:DenseMatrix[Double] = Xc
    val Y:DenseVector[Double] = Yc

    /**
      * Find w and b which minimises ||w|| and constraint violations
      * for a given set of support vectors.
      *
      * @param Xp: Set of integers indicating the indexes of
      *            the support vectors in the training set.
      */
    def findCoefficients(Xp:Set[Int]) : DenseVector[Double] = {
        // get set of non support vectors from the training set
        val nonSV = Set((0 until X.rows).filter(i => !Xp.contains(i)):_*)

        // objective function
        def f(w:DenseVector[Double], R:Set[Int]) : Double = {
            val normw = norm(w(1 to -1))
            val eqCV = violations(Xp, w, i => abs(i) > error)
            val ineqCV = violations(R, w, i => i < 0)

            // λ1||w|| + λ2(% eq violations) + λ3(% ineq violations)
            lambda(0)*normw + lambda(1)*eqCV + lambda(2)*ineqCV
        }

        // initialise search intervals
        val lowerBound:DenseVector[Double] = DenseVector.fill[Double](X.cols+1, wmin)
        val upperBound:DenseVector[Double] = DenseVector.fill[Double](X.cols+1, wmax)

        // perform search
        val best = optimise(nonSV, f, lowerBound, upperBound)

        // return best w
        best._1
    }

    def violations(S:Set[Int], w: DenseVector[Double], comp:Double => Boolean) : Double = {
        val subset = S.toArray.toIndexedSeq
        val Xs:DenseMatrix[Double] = X(subset, ::).toDenseMatrix
        val Ys:DenseVector[Double] = Y(subset).toDenseVector
        val w1 = w(1 to -1)
        val w0 = w(0)
        val c = (0 until Xs.rows).map(i => {
            Ys(i)*(w1.dot(Xs(i, ::).t) + w0) - 1
        })

        // count number of violations
        c.count(i => comp(i)) / Xs.rows.toDouble // normalise
    }

    /**
      * Minimise a given objective function using adaptive coordinate descent.
      * (Specifically for SVM)
      *
      * @param nonSV: Set of non-support vectors from which a random sample is chosen to perform
      *               inequality constraint checks.
      * @param f: the function to minimise
      * @param xmin: a vector defining the lower bound of the starting interval in each dimension
      * @param xmax: a vector defining the upper bound of the starting interval in each dimension
      * @return (xbest, fbest) i.e. the best solution found after a maximum of nIter iterations.
      */
    def optimise(nonSV:Set[Int], f:(DenseVector[Double], Set[Int]) => Double, xmin:DenseVector[Double], xmax:DenseVector[Double]) : (DenseVector[Double], Double) = {
        val d = xmin.length
        val nV:Int = round(cvProp * X.rows).toInt

        // Initialise midpoint
        var m:DenseVector[Double] = DenseVector.tabulate[Double](d)(i => {
            xmin(i) + Uniform(0, xmax(i)-xmin(i)).sample()
        })

        // select initial random violation set
        var R:Set[Int] = Set(shuffle(nonSV.toList).take(nV):_*)

        // Initialise best and archive
        var fbest = f(m, R)
        var xA = DenseMatrix.zeros[Double](2*d, d)
        var fA = DenseVector.zeros[Double](2*d)
        var sig = (xmax - xmin)/4.0
        var ix = 0
        var t = 0

        // Initialise AE
        val ae = new AdaptiveEncoding(d, d, m)

        // Perform coordinate descent
        while (t < maxIters && fbest > tolerance) {
            // increase coordinate index
            ix = (ix + 1) % d

            val xp = DenseVector.zeros[Double](d)

            // X1
            xp(ix) = -sig(ix)
            val x1 = ae.B * xp + m
            val f1 = f(x1, R)

            // X2
            xp(ix) = +sig(ix)
            val x2 = ae.B * xp + m
            val f2 = f(x2, R)

            // test bounds
            var succ = false
            if (f1 < fbest) {
                fbest = f1
                m = x1
                succ = true

            }

            if (f2 < fbest) {
                fbest = f2
                m = x2
                succ = true

            }

            // adjust search interval
            if (succ) {
                sig(ix) = kSucc * sig(ix)
            } else {
                sig(ix) = kUnsucc * sig(ix)
            }

            // save coords
            xA(2*ix-1, ::) := x1.t
            fA(2*ix-1) = f1
            xA(2*ix, ::) := x2.t
            fA(2*ix) = f2

            if (ix == d-1) {
                // sort archive by fitness
                val comb = (0 until xA.rows).map(i => (xA(i, ::).t, fA(i)))
                val sorted = comb.sortBy(_._2)
                xA = DenseMatrix(sorted.map(k => dvDToArray(k._1)):_*)
                fA = DenseVector(sorted.map(k => k._2).toArray)

                // adapt encoding
                ae.update(xA(0 until d, ::))

                // select random violation set for next iteration
                R = Set(shuffle(nonSV.toList).take(nV):_*)
                t += 1
            }
        }

//        printf("fbest = %.10f @ t = %d\n", fbest, t)
        (m, fbest)
    }

}

class AdaptiveEncoding(muc:Int, dc:Int, mc: DenseVector[Double]) {

    // Constants
    val mu:Int = muc
    val d:Int = dc
    val w:DenseVector[Double] = DenseVector.fill[Double](d, 1.0/mu.toDouble)
    val cp:Double = 1.0/sqrt(d)
    val c1:Double = 0.5/d.toDouble
    val cmu:Double = 0.5/d.toDouble

    // Variables
    var p:DenseVector[Double] = DenseVector.zeros[Double](mu)
    var C:DenseMatrix[Double] = DenseMatrix.eye[Double](mu)
    var B:DenseMatrix[Double] = DenseMatrix.eye[Double](mu)
    var Binv:DenseMatrix[Double] = inv(B)
    var m:DenseVector[Double] = mc.copy

    def update(X: DenseMatrix[Double]) : Unit = {
        val mOld = m.copy
        m = sum((0 until mu).map(i => w(i)*X(i, ::).t))

        // denom check
        var z0 = DenseVector.zeros[Double](d)
        if (norm(Binv * (m - mOld)) != 0) {
            z0 = (m - mOld) * sqrt(d)/norm(Binv * (m - mOld))
        }

        val zList = (0 until mu).map(i => {
            if (norm(Binv * (X(i,::).t - mOld)) == 0) {
                dvDToArray(DenseVector.zeros[Double](d))
            } else {
                dvDToArray((sqrt(d)/norm(Binv * (X(i,::).t - mOld))) * (X(i,::).t - mOld))
            }
        })
        val Z:DenseMatrix[Double] = DenseMatrix(zList:_*)

        p = (1- cp)*p + sqrt(cp*(2 - cp))*z0
        val Cmu = sum((0 until mu).map(i => w(i) * (Z(i, ::).t * Z(i, ::))))
        val newC = (1 - c1 - cmu)*C + c1*p*p.t + cmu*Cmu

        // check validity of newC
        val check:Double = sum(newC)
        if (!(check.isNaN || check.isInfinite)) {
            C = newC
        }

        // decomposition
        val eigs = eig(C)
        val Bo = eigs.eigenvectors
        val D = diag(sqrt(abs(eigs.eigenvalues)))
        val Dinv = diag(1.0/sqrt(abs(eigs.eigenvalues)))

        B = Bo*D
        Binv = Dinv * Bo.t
    }
}
