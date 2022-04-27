package problem.evaluator

import org.uma.jmetal.problem.Problem
import org.uma.jmetal.solution.Solution
import org.uma.jmetal.util.evaluator.SolutionListEvaluator
import problem.qualitymeasures.exceptions.InvalidRangeInMeasureException
import problem.qualitymeasures.{ContingencyTable, QualityMeasure}


object Evaluator {
  /**
   * The maximum estimated size of the tree when using a treeReduce function
   */
    val TREE_REDUCE_DEPTH = 20
}


/**
 * An problem.evaluator of individuals to be used in an evolutionary algorithm
 *
 * @param S The type of the solution to be handled
 */
abstract class Evaluator[S <: Solution[_]] extends SolutionListEvaluator[S] with Serializable {
  /**
   * The objectives to be used in the problem.evaluator.
   * These are the objectives employed for guiding the search process and they are used only for its identification.
   */
  private var objectives: Seq[QualityMeasure] = null
  private var problem: Problem[S] = null

  /**
   * GETTERS AND SETTERS
   */
  def getObjectives:Seq[QualityMeasure] = objectives

  def setObjectives(objectives: Seq[QualityMeasure]) = this.objectives = objectives

  def getProblem: Problem[S] = problem

  def setProblem(problem: Problem[S]) = this.problem = problem

  /**
   * It calculates the quality measures given a contingency table
   *
   * @param confMatrix
   */
  def calculateMeasures(confMatrix: ContingencyTable): Seq[Option[QualityMeasure]] = {
    objectives.map(q => {
      try {
        q.calculateValue(confMatrix)
        q.validate()
        Some(q)
      } catch {
        case ex: InvalidRangeInMeasureException => {
          None
        }
      }
    })
  }

  /**
   * It return whether the individual represents the empty pattern or not.
   *
   * @param individual
   * @return
   */
  def isEmpty(individual: Solution[S]): Boolean

  /**
   * It returns whether a given variable of the individual participates in the pattern or not.
   *
   * @param individual
   * @param var
   * @return
   */
  def participates(individual: Solution[S], `var`: Int): Boolean

  /**
   * Initiailises any initial configuration of this problem.evaluator according to the problem
   *
   * @param problem
   */
  def initialise(problem: Problem[S])
}