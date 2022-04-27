package problem.qualitymeasures

import org.uma.jmetal.solution.BinarySolution
import problem.attributes.Clase
import utils.BitSet

/**
 * It is in charge of calculate the support of the whole set of patterns, i.e., all tps / total_examples
 */
object GlobalSupport {

  def apply(patterns: Seq[BinarySolution], instancesOfEachClass: Seq[BitSet]): Double = {

    val globalTPs: BitSet = patterns.map(ind => {
        val cov = ind.getAttribute(classOf[attributes.Coverage[BinarySolution]]).asInstanceOf[BitSet]
        val clazz = ind.getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int]

        cov & instancesOfEachClass(clazz) // get the tps
      })
        .reduce(_ | _)  // join all tps to get the final global tps

      globalTPs.cardinality().toDouble / globalTPs.capacity
  }
}