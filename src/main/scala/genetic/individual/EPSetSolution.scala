package genetic.individual

import org.uma.jmetal.solution.Solution
import problem.EPMProblem
import utils.BitSet

import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable.{Map, MutableList}



class EPSetSolution(val problem: EPMProblem,
                    val initialSize: Int)
  extends Solution[util.List[BitSet]]
  with Serializable
  with Cloneable
{
  val objectives: Array[Double] = Array.ofDim(problem.getNumberOfObjectives)
  val attributes: Map[AnyRef, AnyRef] = Map.empty
  val chromosome: MutableList[BitSet] = MutableList.empty

  override def setObjective(index: Int, value: Double): Unit = objectives(index) = value
  override def getObjective(index: Int): Double = objectives(index)
  override def getObjectives: Array[Double] = objectives
  override def setAttribute(id: AnyRef, value: AnyRef): Unit = attributes += (id -> value)
  override def getAttribute(id: AnyRef): Option[AnyRef] = attributes.get(id)
  override def getAttributes: util.Map[AnyRef, AnyRef] = attributes.asJava
  override def getNumberOfObjectives: Int = objectives.size


  override def getVariableValue(index: Int): util.List[BitSet] = ???

  override def getVariables: util.List[util.List[BitSet]] = ???

  override def setVariableValue(index: Int, value: util.List[BitSet]): Unit = ???

  override def getVariableValueString(index: Int): String = ???

  override def getNumberOfVariables: Int = ???



  override def copy(): Solution[util.List[BitSet]] = ???


}
