package problem.filters

import org.uma.jmetal.solution.Solution
import problem.evaluator.EPMEvaluator

import java.util

@deprecated("Use 'Filters' instead. ", "14/01/2021")
trait Filter[S <: Solution[_] ]{

  /**
   * It perform the specified filter over the solution list
   * @param solutionList The list of solutions
   * @param clase The class of the problem if necessary
   * @param evaluator The information of the evaluation
   * @return A new solution list that passes the filter
   */
  def doFilter(solutionList: util.List[S], clase: Int, evaluator: EPMEvaluator): util.List[S]

}
