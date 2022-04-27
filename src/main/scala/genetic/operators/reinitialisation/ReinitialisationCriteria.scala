package genetic.operators.reinitialisation

import org.uma.jmetal.problem.Problem
import org.uma.jmetal.solution.Solution

import java.io.Serializable
import java.util

trait ReinitialisationCriteria[S <: Solution[_]] extends Serializable {
  /**
   * It checks whether the reinitialisation criteria must be applied or not
   *
   * @param solutionList
   * @return
   */
    def checkReinitialisation(solutionList: util.List[S], problem: Problem[S], generationNumber: Int, classNumber: Int): Boolean

  /**
   * It applies the reinitialisation over the actual solution
   *
   * @param solutionList
   * @return
   */
  def doReinitialisation(solutionList: util.List[S], problem: Problem[S], generationNumber: Int, classNumber: Int): util.List[S]
}