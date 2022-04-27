package algorithms

import attributes.Coverage
import genetic.MyAbstractGeneticAlgorithm
import genetic.individual.EPMBinarySolution
import org.uma.jmetal.operator.{CrossoverOperator, MutationOperator, SelectionOperator}
import org.uma.jmetal.solution.BinarySolution
import org.uma.jmetal.util.solutionattribute.impl.{CrowdingDistance, DominanceRanking}
import problem.attributes.Clase
import problem.conceptdrift.{DriftDetector, MeasureBasedDriftDetector}
import problem.evaluator.{EPMEvaluator, EPMStreamingEvaluator}
import problem.qualitymeasures._
import problem.{EPMStreamingAlgorithm, EPMStreamingProblem}
import utils.BitSet

import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Class that represents a simple mono-objective genetic algorithm for extraction of EPs in data streams.
 *
 * It returns the top-k patterns.
 *
 * @param problem               The information of the EPM Streaming problem
 * @param evaluator             The EPM Streaming evaluator to use (NOTE: ONLY ONE OBJECTIVE IS USED)
 * @param selectionOperator
 * @param crossoverOperator
 * @param mutationOperator
 * @param populationSize        The population size of the algorithm
 * @param maxEvaluations        The maximum number of individuals evaluations to be performed (stopping condition)
 * @param topK                  The number of patterns to return (the TOP-K)
 */
class SimpleStreamingGeneticAlgorithm(problem: EPMStreamingProblem,
                                     val evaluator: EPMStreamingEvaluator,
                                     selectionOperator: SelectionOperator[java.util.List[BinarySolution], BinarySolution],
                                     crossoverOperator: CrossoverOperator[BinarySolution],
                                     mutationOperator: MutationOperator[BinarySolution],
                                     val populationSize: Int,
                                     val maxEvaluations: Int,
                                     val topK: Int)
extends MyAbstractGeneticAlgorithm[BinarySolution, util.List[BinarySolution]](problem, selectionOperator, crossoverOperator,mutationOperator)
    with EPMStreamingAlgorithm
    with Serializable
{
  assert(topK < populationSize && topK > 0, "Top-K number is outside bounds: [1, "+ populationSize + "]." )
  var evaluations: Int = 0
  val PCT_REINIT: Double = 0.15
  var TIMESTAMP: Int = 0
  var CURRENT_CLASS: Int = 0
  var result: Seq[BinarySolution] = Seq.empty
  var archive: Seq[BinarySolution] = mutable.MutableList.empty
  var bestInidividual: Option[(BinarySolution,Int)] = None              // It stores the best pattern found so far in the evolutionary process.

  val measureThreshold: Double = evaluator.getObjectives.head match {
    case x: LinearCombination => 0.6
    case x: WRAccNorm => 0.6
    case x: Confidence => 0.6
    case x: Accuracy => 0.6
    case x: AUC => 0.6
    case x: FPR => 0.3
    case x: GMean => 0.4
    case x: GrowthRate => 3.0
    case x: InverseFPR => 3.0
    case x: Jaccard => 0.3
    case x: OddsRatio => 3.0
    case x: SuppDiff => 0.3
    case x: Support => 0.1
    case x: TNR => 0.1
    case x: TPR => 0.3
    case _ => 0.5
  }

  val driftDetector: DriftDetector = new MeasureBasedDriftDetector(problem,
    evaluator.getObjectives.head match {
      case q: LinearCombination => "CONF"
      case q: QualityMeasure => q.getShortName
    },
    measureThreshold)

  setMaxPopulationSize(populationSize)

  // Ordering of solutions: Best individual is the one with the HIGHEST OBJECTIVE:
  implicit val solutionOrder: Ordering[BinarySolution] = Ordering.by( (x: BinarySolution) => x.getObjective(0)).reverse

  override def getName: String = "SSGA-EPM"
  override def getDescription: String = "Simple Streaming Genetic Algorithm - Emerging Pattern Mining Version"
  override def initProgress(): Unit = evaluations = getMaxPopulationSize
  override def isStoppingConditionReached: Boolean = evaluations >= maxEvaluations
  override def getResult: util.List[BinarySolution] = result.asJava

  /**
   * The initial population is created by introducing the previous model in the streaming
   * @return
   */
  override def createInitialPopulation(): util.List[BinarySolution] = {
    archive = mutable.MutableList.empty
    val newPop: Seq[BinarySolution] = this.problem.getPreviousResultsStreaming() match {

      case Some(model) => {
        val p = model.filter(ind => ind.getAttribute(classOf[Clase[BinarySolution]]) == CURRENT_CLASS)  // get individuals of the current class
          .map(ind => {
            ind.getAttributes.remove(classOf[DominanceRanking[BinarySolution]])  // Remove attributes of the previous run.
            ind.getAttributes.remove(classOf[CrowdingDistance[BinarySolution]])
            ind
          })
        val numIndividualsToGenerate = getMaxPopulationSize - p.size
        // Check if the population is greater than the maximum population. If so, prune
        if(numIndividualsToGenerate < 0){
          p.sorted.slice(0, getMaxPopulationSize)
        } else {
          val generatedIndividuals = (0 until numIndividualsToGenerate).map(x => getProblem.createSolution())
          p ++ generatedIndividuals
        }
      }

      case None =>  {  // Generate the whole random population
        (0 until getMaxPopulationSize).map(x => getProblem.createSolution())
      }
    }

    newPop.asJava
  }

  override def evaluatePopulation(population: util.List[BinarySolution]): util.List[BinarySolution] = {
    evaluator.evaluate(population,problem)
  }

  override def replacement(population: util.List[BinarySolution], offspringPopulation: util.List[BinarySolution]): util.List[BinarySolution] = {
    val elitismSize = 2

    val bestParents = population.asScala
      .sorted
      .take(elitismSize)

    val newPop = offspringPopulation.asScala
      .sorted    // Sort by objective (see implicit ordering at line 84
      .dropRight(elitismSize) ++ bestParents // drop the k worsts elements in the offspring (elitism) and the bestParents

    newPop.sorted.asJava
  }


  override def updateProgress(): Unit = {
    evaluations += getMaxPopulationSize
    val currentBest: BinarySolution = population.get(0)

    // update the current best individual and check for reinitialisation
    bestInidividual match {
      case Some(ind) => {
        bestInidividual = if(ind._1.getObjective(0) < currentBest.getObjective(0)) {    // If we find a new best. Update it with the current evaluation
          Some((currentBest.copy().asInstanceOf[EPMBinarySolution], evaluations))
        } else {
          Some(ind)
        }

        bestInidividual.get match {
          // apply reinitialisation if necessary:
            // Steps: 1) adds the best to the archive. 2) removes its instances marked as TP. 3) set best individuals as NONE.
          case (individual: BinarySolution, indEvaluation: Int) if (evaluations - indEvaluation > maxEvaluations * PCT_REINIT) => {
            archive = individual +: archive
            removeCorrectlyCoveredDataPoints(List(individual), evaluator)
            bestInidividual = None

            // re-initialise the population
            population = (0 until getMaxPopulationSize).map(i => getProblem.createSolution()).asJava
            population = evaluator.evaluate(population, getProblem)
            // Special case: All instances have been correctly covered. Stop the GA.
            if(evaluator.classes(CURRENT_CLASS).cardinality() == 0) evaluations = maxEvaluations + 3
          }
          case _ => {}
        }
      }

        // if not defined, the current best is just updated
      case None => bestInidividual = Some((currentBest.copy().asInstanceOf[EPMBinarySolution], evaluations))
    }

    /* TODO: We can try to reinitialise the population by storing the best pattern in the archive,
        and removing the data points covered by that pattern as TP, so the quality of the extracted pattern is reduced.
        This would allow us to extract pattern for all the possible data points ??
     */

  }



  override def run(): Unit = {
    //TIMESTAMP += 1

    // TEST-THEN-TRAIN. First, test the results
    val model: Seq[BinarySolution] = problem.getPreviousResultsStreaming() match {
      case Some(model) => test(problem,evaluator)(model)
      case None => test(problem, evaluator)(Seq.empty[BinarySolution])
    }

    // AFTER TEST, TRAIN
    // Generate fuzzy sets definitions only once.
    train(problem,evaluator)(model)

    problem.replacePreviousResults(getResult.asScala)
  }


  /**
   * It test the current model
   *
   * @param model
   * @param evaluator
   * @return
   */
  def test(problem: EPMStreamingProblem, evaluator: EPMStreamingEvaluator)(model: Seq[BinarySolution]): Seq[BinarySolution] = {
    TIMESTAMP += 1
    val toRun = evaluator.classes   // Get the classes with examples in this batch:
      .map(_.cardinality() > 0)     // Check if there is examples for the class
      .zipWithIndex                 // Get the index of the class to be executed
      .filter(_._1)                 // Get only the elements with TRUE values
      .map(_._2)                    // Get the integer of the class

    if (model.nonEmpty) {
      // Get only patterns of the classes with examples in this new batch
      val filteredModel = model.filter(toRun contains _.getAttribute(classOf[Clase[BinarySolution]])).asJava

      evaluator.evaluateTest(filteredModel, problem) // Evaluate and enqueue
      evaluator.enqueue(filteredModel)

      if (!filteredModel.isEmpty) {
        // write the results in the file (return to expert)
        writer.setPopulation(filteredModel.asScala)
        writer.writeStreamingResults(TIMESTAMP, getExecutionTime(), getMemory())
      }
    }
    model
  }


  def train(problem: EPMStreamingProblem, evaluator: EPMStreamingEvaluator)(model: Seq[BinarySolution]): Seq[BinarySolution] = {
    val toRun = evaluator.classes   // Get the classes with examples in this batch:
      .map(_.cardinality() > 0)     // Check if there is examples for the class
      .zipWithIndex                 // Get the index of the class to be executed
      .filter(_._1)                 // Get only the elements with TRUE values
      .map(_._2)                    // Get the integer of the class

    learn(toRun)

    // Drift detection mechanism moved to DistributedDevice.
    /* TIMESTAMP match {

      case t if t <= 1 => learn(toRun)

      case _ => {
        val toExec: Seq[Int] = driftDetector.detect(model, toRun).toSeq
        if(toExec.nonEmpty){
          learn(toRun)
        } else {
          model
        }
      }
    } */

  }


  /**
   * It performs the execution of the GA on the necessary classes
   * @param classesToRun
   * @return
   */
  def learn(classesToRun: Seq[Int]): Seq[BinarySolution] = {

     // Keep in the result those patterns whose classes are not processed here
    result = result.filter(ind => {
      val cl: Int = ind.getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int]
      ! (classesToRun.contains(cl))
    })

    result = result ++
      classesToRun.map(clas => {
        CURRENT_CLASS = clas
        problem.fixClass(CURRENT_CLASS)
        super.run()  // Run the genetic algorithm

        evaluator.forbiddenInstances = BitSet.empty
        // after runing the GA
        if(archive.isEmpty) {
          population.asScala.minBy(_.getObjective(0)) +: archive
        } else{
          archive.distinct                            // Remove the repeated patterns and return the top-k patterns
            .sorted
            .take(topK)
        }
      }).reduce(_ ++ _)

    // At the end, replace the previous results
    result
  }

  /**
   * it removes the correctly covered instances of a given set of pattern from the current dataset.
   *
   * Note: It does the removal by setting to zero in the evaluator the coverage of each selector and in the classes of each class.
   *
   * @param data
   * @param patterns
   * @param evaluator
   * @return
   */
  def removeCorrectlyCoveredDataPoints(patterns: Seq[BinarySolution], evaluator: EPMEvaluator): Unit = {

    val coverages: Seq[BitSet] = patterns.map(_.getAttribute(classOf[Coverage[BinarySolution]]).asInstanceOf[BitSet])

    val truePositives: BitSet = patterns.zip(coverages)
      .map(i => {
        val cl: Int= i._1.getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int]

        // Return the true positives
        i._2 & evaluator.classes(cl)
      })
      .reduce(_ | _)

    // Remove these true positive of the evaluator in sets and in classes
    evaluator.addForbiddenInstances(truePositives)

  }

}
