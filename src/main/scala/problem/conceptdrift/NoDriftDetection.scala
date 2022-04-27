package problem.conceptdrift

import org.uma.jmetal.solution.BinarySolution
import problem.EPMStreamingProblem

/**
 * Class that represents the absence of a drift detection method. So it returns all classes of the problem
 * @param problem
 */
class NoDriftDetection(problem: EPMStreamingProblem) extends DriftDetector(problem) {

  /**
   * It detects the concept drift
   *
   * @param model The current pattern model
   * @return A set of ints representing those classes which have suffered concept drift
   */
  override def detect(model: Seq[BinarySolution], batchClasses: Seq[Int]): Iterable[Int] = batchClasses
}
