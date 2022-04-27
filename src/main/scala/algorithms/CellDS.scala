package algorithms

import attributes.Coverage
import net.sourceforge.jFuzzyLogic.membership.MembershipFunction
import org.uma.jmetal.algorithm.multiobjective.mocell.MOCell
import org.uma.jmetal.operator.{CrossoverOperator, MutationOperator, SelectionOperator}
import org.uma.jmetal.solution.BinarySolution
import org.uma.jmetal.util.JMetalLogger
import org.uma.jmetal.util.archive.impl.CrowdingDistanceArchive
import org.uma.jmetal.util.neighborhood.util.TwoDimensionalMesh
import org.uma.jmetal.util.solutionattribute.impl.{CrowdingDistance, DominanceRanking}
import problem.attributes.Clase
import problem.conceptdrift.{DriftDetector, MeasureBasedDriftDetector}
import problem.evaluator.{EPMEvaluator, EPMStreamingEvaluator}
import problem.filters.{CoverageRatioFilter, Filter}
import problem.{EPMStreamingAlgorithm, EPMStreamingProblem, Fuzzy}
import utils.BitSet
import weka.core.Instances

import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class CellDS ( problem: EPMStreamingProblem,
             confidenceThreshold: Double = 0.6,
             supportThreshold: Double = 0.1,
             maxEvaluations: Int,
             populationSize: Int,
             crossoverOperator: CrossoverOperator[BinarySolution],
             mutationOperator: MutationOperator[BinarySolution],
             selectionOperator: SelectionOperator[util.List[BinarySolution], BinarySolution],
             neighbourhoodType: TwoDimensionalMesh[BinarySolution],
             evaluator: EPMStreamingEvaluator,
               filters: Seq[Filter[BinarySolution]] = List(new CoverageRatioFilter[BinarySolution](1)))
extends MOCell[BinarySolution] (
  problem,
  maxEvaluations,
  populationSize,
  new CrowdingDistanceArchive[BinarySolution](populationSize),
  neighbourhoodType,
  crossoverOperator,
  mutationOperator,
  selectionOperator,
  evaluator
) with EPMStreamingAlgorithm with Serializable
{


  /**
   * ELEMENTS OF THE GENETIC ALGORITHM
   */

  /** The elite population, where the best patterns found so far are stored */
  private var elite: util.List[BinarySolution] = new util.ArrayList[BinarySolution]()

  /** A bit set which marks the instances covered on the previous generations. This is for checking the re-init criteria */
  private var previousCoverage: BitSet = new BitSet(1)

  /** The generation (or evaluation) where the last change in {@code previousCoverage} occurred */
  private var lastChange = 0

  /** The percentage of generations (or evaluations) without change for triggering the re-initialisation opeartor */
  private val REINIT_PCT: Float = 0.25f

  /** For oriented initialisation: maximum percentage of variables randomly initialised. */
  private val PCT_COVERAGE: Double = 0.25

  /** For oriented initialisation: maximum percentage of individuals where only {@code PCT_COVERAGE} of its variables are initialised */
  private val PCT_INDS_ORIENTED: Double = 0.75


  /** The timestamp of the data stream we are analysing */
  private var TIMESTAMP = 0

  /** Class for writting the results extracted into a file */
  /*var writer = new ResultWriter(
    "tra",
    "tst",
    "tra_summ",
    "rules",
    null,
    problem,
    evaluator.getObjectives,
    true)*/



  /** The drift detection method(s) */
  val realDriftDetector: DriftDetector = new MeasureBasedDriftDetector(problem, "CONF", confidenceThreshold)
  val virtualDriftDetector: DriftDetector = new MeasureBasedDriftDetector(problem,"Supp", supportThreshold)


  /**
   * The current class to be processed.
   */
  private var CURRENT_CLASS = 0;


  /**
   * The initial population is created by adding the previously extracted individuals
   *
   * @return
   */
  override def createInitialPopulation(): util.List[BinarySolution] = {

    problem.fixClass(CURRENT_CLASS)
    archive.getSolutionList.clear()

    val newPop: Seq[BinarySolution] = this.problem.getPreviousResultsStreaming() match {

      case Some(model) => {
        val p = model.filter(ind => ind.getAttribute(classOf[Clase[BinarySolution]]) == CURRENT_CLASS)  // get individuals of the current class
          .map(ind => {
            ind.getAttributes.remove(classOf[DominanceRanking[BinarySolution]])  // Remove attributes of the previous run.
            ind.getAttributes.remove(classOf[CrowdingDistance[BinarySolution]])
            ind
          })
        val numIndividualsToGenerate = getMaxPopulationSize - p.size
        // TODO: Check if the population is greater than the maximum population. If so, prune
        // TODO: Maybe is better to keep the pareto front only.
        if(numIndividualsToGenerate < 0){
          p.slice(0, getMaxPopulationSize)
        } else {
          val generatedIndividuals = (0 until numIndividualsToGenerate).map(x => getProblem.createSolution())
          (p ++ generatedIndividuals)
        }
      }

      case None =>  {  // Generate the whole random population
        (0 until getMaxPopulationSize).map(x => getProblem.createSolution())
      }
    }

    newPop.asJava
  }


  /**
   * Updating the progress in CellDS is performed by checking the reinitialisation criterion and reinitialising if necessary.
   * Also, the elite population is updated
   */
  override def updateProgress(): Unit = {
    super.updateProgress() // ONLY NSGA-II (updates the evaluation number)

    JMetalLogger.logger.finest("Evaluations: " + evaluations)

    // Check reinitialisation
    if(evaluations % populationSize == 0) { // check reinitialisation criteria when all individuals are evaluated.
      if (checkReinitialisationCriterion(getPopulation, this.previousCoverage, lastChange, REINIT_PCT)) {
        JMetalLogger.logger.finest("Reinitialisation at evaluation " + evaluations + " out of " + maxEvaluations)
        population = coverageBasedInitialisation(population, evaluator.classes(CURRENT_CLASS))

        // evaluate the new generated population to avoid errors
        evaluator.evaluate(population, problem)
      }
    }
  }

  /**
   * The result is the elite population
   *
   * @return
   */
  override def getResult: util.List[BinarySolution] = elite

  override def getName: String = "CellDS"


  /**
   * Test the current model
   * @param problem
   * @param evaluator
   * @param model
   *  @return
   */
  override  def test(problem: EPMStreamingProblem, evaluator: EPMStreamingEvaluator)(model: Seq[BinarySolution]): Seq[BinarySolution] = {
    val toRun = evaluator.classes   // Get the classes with examples in this batch:
      .map(_.cardinality() > 0)     // Check if there is examples for the class
      .zipWithIndex                 // Get the index of the class to be executed
      .filter(_._1)                 // Get only the elements with TRUE values
      .map(_._2)                    // Get the integer of the class

    if (!model.isEmpty) {
      // Get only patterns of the classes with examples in this new batch
      val filteredModel = model.filter(toRun contains _.getAttribute(classOf[Clase[BinarySolution]])).asJava

      evaluator.evaluateTest(filteredModel, problem) // Evaluate and enqueue
      evaluator.enqueue(filteredModel)

      if (!filteredModel.isEmpty) {
        // write the results in the file (return to expert)
        writer.setPopulation(filteredModel.asScala)
        writer.writeStreamingResults(TIMESTAMP, getExecutionTime(), getMemory())
      }

      // TODO: Try removing those correctly covered instances from the incoming data before training.
      // TODO: WARNING!! It is possible that patterns in model are removed due to bad quality on the current data.
      //removeCorrectlyCoveredDataPoints(problem.getData, model.asScala, evaluator)
    }

    model
  }

  /**
   * Update the model with the current data
   *
   */
  override def train(problem: EPMStreamingProblem, evaluator: EPMStreamingEvaluator)(model: Seq[BinarySolution]): Seq[BinarySolution] = {
    val toRun = evaluator.classes   // Get the classes with examples in this batch:
      .map(_.cardinality() > 0)     // Check if there is examples for the class
      .zipWithIndex                 // Get the index of the class to be executed
      .filter(_._1)                 // Get only the elements with TRUE values
      .map(_._2)                    // Get the integer of the class

    if(TIMESTAMP <= 1) {
      JMetalLogger.logger.info("Generating the Fuzzy Sets Definitions at timestamp " + TIMESTAMP)
      //QualityMeasure.setMeasuresReversed(true)  // MOCell implementation minimises the objectives.
      startLearningProcess(toRun)
      elite.asScala
    } else {

      // Drift detection and train if necessary
      val toExecRealDrift: Seq[Int] = realDriftDetector.detect(model, toRun).toSeq
      val toExecVirtualDrift: Seq[Int] = virtualDriftDetector.detect(model, toRun).toSeq
      val toExec: Seq[Int] = (toExecRealDrift union toExecVirtualDrift).distinct

      if(toExec.nonEmpty){
        // A drift on some classes have been detected, run the algorithm
        JMetalLogger.logger.info("DRIFT DETECTED: From Real Drift: " + toExecRealDrift + "  -   From Virtual Drift: " + toExecVirtualDrift)
        //QualityMeasure.setMeasuresReversed(true)  // MOCell implementation minimises the objectives.
        //updateFuzzySets(problem)  // Always try to update fuzzy sets definitions due to data can move out of the boundaries
        startLearningProcess(toExec)
        elite.asScala
      } else {
        JMetalLogger.logger.info("Do not train for timestamp " + TIMESTAMP)
        model
      }
    }

  }

  override def run(): Unit = {
    TIMESTAMP += 1

    // TEST-THEN-TRAIN. First, test the results
    val model: Seq[BinarySolution] = problem.getPreviousResultsStreaming() match {
      case Some(model) => test(problem,evaluator)(model)
      case None => test(problem, evaluator)(Seq.empty[BinarySolution])
    }

    // AFTER TEST, TRAIN
    // Generate fuzzy sets definitions only once.
    train(problem,evaluator)(model)

  }



  /**
   * It starts the learning procedure for the extraction of a new pattern set model
   */
  private def startLearningProcess(classesToRun: Seq[Int]): Unit = {
    JMetalLogger.logger.fine("Starting " + getName + " execution.")

    // Keep in the elite those patterns whose classes are not processed
    elite = elite.asScala.filter(ind => {
      val cl: Int = ind.getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int]
      ! (classesToRun.contains(cl))
    }).asJava

    classesToRun.foreach(clas => {
      CURRENT_CLASS = clas
      previousCoverage.clear()
      lastChange = 0

      try {
        super.run() // Run MOCell with the overrided methods

        // Return the non-dominated individuals, but first, evalute the population
        val nonDominatedPopulation = archive.getSolutionList //getResult
        JMetalLogger.logger.finest("Size Of NonDominatedPopulation: " + nonDominatedPopulation.size())

        // Add to the elite the result of applying the filters on the nonDominatedPopulation
        var individuals: util.List[BinarySolution] = nonDominatedPopulation
        for (filter <- filters) {
          individuals = filter.doFilter(individuals, CURRENT_CLASS, this.evaluator.asInstanceOf[EPMEvaluator])
        }

        JMetalLogger.logger.info("Size after Filters: " + individuals.size())
        elite.addAll(individuals)
      } catch {
        case e: Exception => {
          // Somethinig went wrong. Notify and return an empty list
          println("An error has occurred at processing timestamp " + TIMESTAMP + " for class " + clas + ". Please check logs.")
          JMetalLogger.logger.severe(e.getMessage + ":\n" + e.printStackTrace())
          //elite.addAll(new util.ArrayList[BinarySolution]())
        }
      }
    })

    // At the end, replace the previous results with this one
    this.problem.replacePreviousResults(elite.asScala)
    //println("-------------------------")
    JMetalLogger.logger.fine("Finished " + getName + " execution.")

  }



  /**
   * It checks whether the population must be reinitialised.
   *
   * @param population       The population
   * @param previousCoverage The coverage of the previous population
   * @param lastChange       The evaluation where the last change in the population's coverage have been made
   * @param percentage       The maximum percentage of evaluation to allow no evolution.
   * @return
   */
  private def checkReinitialisationCriterion(population: util.List[BinarySolution], previousCoverage: BitSet, lastChange: Int, percentage: Float): Boolean = {

    // Get the current coverage of the whole population
    val currentCoverage: BitSet = population.asScala.map(ind => ind.getAttribute(classOf[Coverage[BinarySolution]]).asInstanceOf[BitSet]).reduce(_ | _)

    if (previousCoverage == null) {
      this.previousCoverage = currentCoverage.copy
      this.lastChange = evaluations
      return false
    }

    if (previousCoverage.cardinality() == 0 && currentCoverage.cardinality() != 0) {
      this.previousCoverage = currentCoverage.copy
      this.lastChange = evaluations
      return false
    }
    // Calculate if there are new covered examples not previously covered
    val newCovered: BitSet = (previousCoverage ^ currentCoverage) & (~previousCoverage)

    if (newCovered.cardinality() > 0) {
      JMetalLogger.logger.finer("New examples covered at evaluation: " + evaluations)
      this.previousCoverage = currentCoverage.copy
      this.lastChange = evaluations
      return false
    } else {
      val evalsToReinit = (maxEvaluations * percentage).toInt
      return (evaluations - lastChange) >= evalsToReinit
    }
  }



  /**
   * It performs the coverage-based re-initialisation procedure.
   *
   * @param population
   * @param clase
   */
  private def coverageBasedInitialisation(population: util.List[BinarySolution], clase: BitSet): util.List[BinarySolution] = {
    // First of all, perform COVERAGE RATIO filter
    val newPopulation = filters(0).doFilter(population, CURRENT_CLASS, evaluator)
    lastChange = evaluations // iterations


    // Then get the coverage of the result and determines the number of new individuals that must be generated
    val currentCoverage = if (newPopulation.isEmpty) {
      new BitSet(clase.capacity)
    } else {
      newPopulation.asScala.map(ind => ind.getAttribute(classOf[Coverage[BinarySolution]]).asInstanceOf[BitSet]).reduce(_ | _)
    }
    previousCoverage = currentCoverage.copy

    // Find a non-covered example of the class
    val nonCoveredExamples = ~currentCoverage & clase

    if (nonCoveredExamples.cardinality() > 0) {
      // At least an example of the class is not covered
      while (nonCoveredExamples.nextSetBit(0) != -1 && newPopulation.size() < populationSize) {
        val example = nonCoveredExamples.nextSetBit(0)
        //val pairs = evaluator.getPairsCoveringAnExample(example)

        val newIndividual: BinarySolution = problem.coverageBasedInitialisation(example, evaluator, PCT_COVERAGE)
        newPopulation.add(newIndividual)

        // Mark the example as covered in order to cover a new one
        nonCoveredExamples.unset(example)
      }

      // If there are remaining individuals, generate them randomly
      if (newPopulation.size() < populationSize) {
        val newInds = (0 until (populationSize - newPopulation.size())).map(x => getProblem.createSolution())
        newPopulation.addAll(newInds.asJava)
      }
      newPopulation
    } else {
      // All examples of the class have been already covered: Random initialisation
      val newInds = (0 until (populationSize - newPopulation.size())).map(x => getProblem.createSolution())
      newPopulation.addAll(newInds.asJava)
      newPopulation
    }


  }


  /**
   * It updates the fuzzy sets definitions with respect to the current data chunk
   *
   * The update is performed by means of updating the new min and max values and then re-do the LLs definitions.
   *
   * @param problem  The EPM Problem
   */
  private def updateFuzzySets(problem: EPMStreamingProblem): Unit = {
    val numLabels = problem.getNumLabels()

    for (i <- 0 until problem.getNumberOfVariables) {
      if (problem.getData.attribute(i).isNumeric) {
        var currentMin = problem.getFuzzySet(i, 0).getUniverseMin
        var currentMax = problem.getFuzzySet(i, numLabels - 1).getUniverseMax
        var changed = false
        val dat = problem.getData.attributeToDoubleArray(i)
        val dataMin = dat.min
        val dataMax = dat.max

        if(dataMin < currentMin) {
          currentMin = dataMin
          changed = true
        }
        if(dataMax > currentMax) {
          currentMax = dataMax
          changed = true
        }

        if(changed){
          JMetalLogger.logger.info("FUZZY DEFINITIONS CHANGED !!")
          problem.getFuzzyVariable(i).asInstanceOf[ArrayBuffer[MembershipFunction]].clear()
          problem.getFuzzyVariable(i).asInstanceOf[ArrayBuffer[MembershipFunction]].insertAll(0, Fuzzy.generateTriangularLinguisticLabels(currentMin, currentMax, numLabels))
        }
      }
    }
  }


  /**
   * It removes the correctly covered instances from the stream.
   *
   *  It returns an __Instances__ class with the instances that are not correctly covered.
   *
   * @param data          The Instances class with the data to process
   * @param patterns      The patterns extracted
   * @param evaluator      The evaluator employed to compute the true positives
   * @return      A new __Instances__ class with the data processed
   */
  def removeCorrectlyCoveredDataPoints(data: Instances, patterns: Seq[BinarySolution], evaluator: EPMEvaluator): Instances = {
    val coverages: Seq[BitSet] = patterns.map(_.getAttribute(classOf[Coverage[BinarySolution]]).asInstanceOf[BitSet])
    val newInstances: Instances = new Instances(data, 0,0) // empty Instances set

   val truePositives: BitSet = patterns.zip(coverages)
     .map(i => {
       val cl: Int= i._1.getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int]

       // Return the true positives
       i._2 & evaluator.classes(cl)
     })
     .reduce(_ | _)

    // now, remove those instances sets
    for(
      i <- 0 until data.size
      if !truePositives.get(i)
    )
      yield newInstances.add(data.get(i))

    newInstances
  }

}
