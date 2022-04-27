package problem.conceptdrift

import org.uma.jmetal.solution.BinarySolution
import problem.EPMStreamingProblem


/**
 * Abstract class that represents a drift detection method for a given EPM Streaming problem.
 *
 * @param problem
 */
abstract class DriftDetector(val problem: EPMStreamingProblem) {

  /**
   * It detects the concept drift
   *
   * @param model     The current pattern model
   * @return          A set of ints representing those classes which have suffered concept drift
   */
  def detect(model: Seq[BinarySolution], batchClasses: Seq[Int]): Iterable[Int]

}
