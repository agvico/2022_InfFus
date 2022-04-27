package problem

import org.uma.jmetal.algorithm.Algorithm
import org.uma.jmetal.solution.BinarySolution
import problem.evaluator.EPMStreamingEvaluator
import utils.ResultWriter

import java.util


trait EPMStreamingAlgorithm extends Algorithm[util.List[BinarySolution]]{

  private var EXECUTION_TIME: Long = 0
  private var MEMORY: Double = 0
  protected var writer: ResultWriter = _

  /**
    * It sets the execution time that the method takes on its processing
    * @param time
    */
  def setExecutionTime(time: Long) = EXECUTION_TIME = time

  def getExecutionTime(): Long  = EXECUTION_TIME

  /**
    * It sets the amount of memory that the algorithm consumes on its processing
    * @param memory
    */
  def setMemory(memory: Double) = MEMORY = memory

  def getMemory(): Double = MEMORY


  def setResultWriter(wr: ResultWriter): Unit = writer = wr


  /**
   * It test the current model
   * @param model
   * @param evaluator
   * @return
   */
  def test(problem: EPMStreamingProblem, evaluator: EPMStreamingEvaluator)(model: Seq[BinarySolution]): Seq[BinarySolution]
  def train(problem: EPMStreamingProblem, evaluator: EPMStreamingEvaluator)(model: Seq[BinarySolution]): Seq[BinarySolution]

}
