package genetic.operators.reinitialisation

import attributes.Coverage
import org.uma.jmetal.problem.Problem
import org.uma.jmetal.solution.Solution
import org.uma.jmetal.util.solutionattribute.impl.DominanceRanking
import problem.EPMProblem
import problem.attributes.Clase
import utils.BitSet

import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class NonEvolutionReinitialisation[S <: Solution[_]](threshold: Int, numClasses: Int, numExamples: Int) extends ReinitialisationCriteria[S]{

  /**
    * Generation where the last change in the population occurred
    */
    private var lastChange: Array[Int] = new Array[Int](numClasses)

    private var previousCoverage: Array[BitSet] = new Array[BitSet](numClasses)
    for(i <- previousCoverage.indices){
      previousCoverage(i) = new BitSet(numExamples)
    }

  /**
    * It checks whether the reinitialisation criteria must be applied or not
    *
    * @param solutionList
    * @return
    */
  override def checkReinitialisation(solutionList: util.List[S], problem: Problem[S], evaluationsNumber: Int, classNumber: Int): Boolean = {
    var coverageTotal = new BitSet(previousCoverage(classNumber).capacity)
    val clase = classNumber
    val pop = solutionList.asScala.filter( (x:S) => x.getAttribute(classOf[Clase[S]]).asInstanceOf[Int] == clase)

    for(i <- pop.indices){
      val rank = pop(i).getAttribute(classOf[DominanceRanking[S]]).asInstanceOf[Int]
      if(rank == 0){
        val coverage = pop(i).getAttribute(classOf[Coverage[S]]).asInstanceOf[BitSet]
        coverageTotal = coverageTotal | coverage
      }
    }
    val newIndsCovered = (previousCoverage(classNumber) ^ coverageTotal) & (~previousCoverage(classNumber))
    previousCoverage(classNumber) = coverageTotal

    if(newIndsCovered.cardinality() > 0 ){
      lastChange(classNumber) = evaluationsNumber
      return false
    }

    return evaluationsNumber - lastChange(classNumber) >= threshold
  }

  /**
    * It applies the reinitialisation over the current solution
    *
    * @param solutionList
    * @return
    */
  override def doReinitialisation(solutionList: util.List[S], problem: Problem[S], generationNumber: Int, classNumber: Int): util.List[S] = {
    null
  }



  def coverageBasedInitialisation(elitePop: util.List[S], problem: EPMProblem): Unit ={

  }

  def getHashCode(ind: S): Int = {
    var result = util.Arrays.hashCode(ind.getObjectives)
    for(i <- 0 until ind.getNumberOfVariables){
      result = 31 * result + ind.getVariableValue(i).hashCode()
    }
    result
  }


  def removeRepeated(solutionList: util.List[S]): util.List[S] = {
    val marks = new util.BitSet(solutionList.size())

    for(i <- 0 until solutionList.size()){
      val hash = getHashCode(solutionList.get(i))
      for(j <- i until solutionList.size()){
        if(i!=j && getHashCode(solutionList.get(j)) == hash){
          marks.set(j)
        }
      }
    }

    val toReturn = new ArrayBuffer[S]()
    for(i <- 0 until solutionList.size()){
      if(!marks.get(i)){
        toReturn += solutionList.get(i)
      }
    }

    toReturn.asJava

  }
}
