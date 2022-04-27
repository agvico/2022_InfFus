package problem.qualitymeasures

import org.uma.jmetal.solution.BinarySolution
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute


sealed trait QualityMeasure2
  extends GenericSolutionAttribute[BinarySolution, Double]
    with Cloneable
    with Serializable
    with Ordered[QualityMeasure2]
{
  val value: Double
  val t: ContingencyTable
  val name: String
  val shortName: String
  val isMinimisable: Boolean

  override def compare(that: QualityMeasure2): Int = {
    val comparison: Int = {
      if(this.value < that.value) -1
      else if(this.value == that.value) 0
      else 1
    }

    if(isMinimisable) -comparison else comparison

  }

  /**
   * QUALITY MEASURES BELOW:
   */

  case class Accuracy2(t: ContingencyTable) extends QualityMeasure2 {
    override val value: Double = if(t.getTotalExamples != 0) (t.getTp + t.getTn) / t.getTotalExamples else 0.0
    override val name: String = "Accuracy"
    override val shortName: String = "ACC"
    override val isMinimisable: Boolean = false
  }

}






