package problem.evaluator
import attributes.TestMeasures
import org.uma.jmetal.problem.Problem
import org.uma.jmetal.solution.BinarySolution
import problem.qualitymeasures.QualityMeasure

import java.util
import scala.collection.mutable
import scala.collection.mutable.{HashMap, Map, Queue}


class EPMStreamingEvaluator(queueSize: Int) extends EPMEvaluator {


  /**
    * The fifo Queue that contains a Map of solutions with its corresponding quality measures
    */
  val FIFO: Queue[Map[BinarySolution, Seq[QualityMeasure]]] = Queue.empty

  

  /**
   * Evaluate the training data as usual. After that, apply the decay/reward factor
   * @param solutionList
   * @param problem
   * @return
   */
  override def evaluate(solutionList: util.List[BinarySolution], problem: Problem[BinarySolution]): util.List[BinarySolution] = {

    // First, evaluate the population as usual
    val population = super.evaluate(solutionList, problem)

    // Now, apply the reward process (ONLY IF QUEUE SIZE IS HIGHER THAN ZERO)

    for(i <- 0 until population.size()){
      var exponent: Int = -FIFO.length // We reverse as the last one is the most recent one
      val individual = population.get(i)
      val reward: Array[Double] = Array.fill(problem.getNumberOfObjectives)(0.0)

      for (set <- FIFO) { // For each previous stage
        set.get(individual) match {
          case Some(qualityMeasures) => { // The individual was in this stage, add its reward
            for (j <- this.getObjectives.indices) {
              val qm = qualityMeasures.filter(x => {
                x.getClass.equals(this.getObjectives(j).getClass)
              }).head
              reward(j) += qm.getValue * Math.pow(2, exponent)
            }
          }
          case None => {}  // The individual was not in this stage, do nothing
        }
        exponent += 1
      }

      for (j <- 0 until problem.getNumberOfObjectives) {
        individual.setObjective(j, individual.getObjective(j) + reward(j))
      }

    }

    population
  }

  /**
    * it adds the to the FIFO queue the given individuals, removing duplicates if necessary and discarding old elements
    * if the size exceeds the maximum
    *
    * @param solutionList
    */
  def enqueue(solutionList: util.List[BinarySolution]): Unit ={

   if(queueSize > 0) {
     val set: Map[BinarySolution, Seq[QualityMeasure]] = new HashMap[BinarySolution, Seq[QualityMeasure]]()

     for (i <- 0 until solutionList.size()) {
       val testMeasures = solutionList.get(i).getAttribute(classOf[TestMeasures[BinarySolution]]).asInstanceOf[mutable.ArraySeq[QualityMeasure]]
       set += solutionList.get(i).copy().asInstanceOf[BinarySolution] -> testMeasures.clone()
     }

     FIFO.enqueue(set)
     if (FIFO.length > queueSize) { // Remove oldest element if the size of the window is bigger than the specified
       FIFO.dequeue()
     }
   }

  }
}
