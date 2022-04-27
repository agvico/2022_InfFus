//package algorithms.datastream
//
//import java.util
//import java.util.Comparator
//
//import attributes.Coverage
//import org.uma.jmetal.algorithm.multiobjective.nsgaii.NSGAII
//import org.uma.jmetal.operator.impl.selection.BinaryTournamentSelection
//import org.uma.jmetal.operator.{CrossoverOperator, MutationOperator, SelectionOperator}
//import org.uma.jmetal.solution.BinarySolution
//import org.uma.jmetal.util.comparator.{DominanceComparator, RankingAndCrowdingDistanceComparator}
//import org.uma.jmetal.util.solutionattribute.impl.{CrowdingDistance, DominanceRanking}
//import org.uma.jmetal.util.{JMetalLogger, SolutionListUtils}
//import problem.attributes.Clase
//import problem.conceptdrift.{DriftDetector, NoDriftDetection}
//import problem.evaluator.{EPMEvaluator, EPMStreamingEvaluator}
//import problem.filters.{Filter, TokenCompetitionFilter}
//import problem.qualitymeasures.QualityMeasure
//import problem.{EPMStreamingAlgorithm, EPMStreamingProblem}
//import utils.{BitSet, ResultWriter}
//
//import scala.collection.JavaConverters._
//
//
///**
//  * Class that represents the FEPDS algorithm proposed in
// *
// * '''FEPDS: A Proposal for the Extraction of Fuzzy Emerging Patterns in Data Streams,
// * García-Vico, A.M., Carmona C. J., González P., Seker H., and del Jesus M. J. ,
// * IEEE Transactions on Fuzzy Systems, p.1-12, (In Press).'''
//  *
//  * @param problem                 An instance of EPMStreamingProblem
//  * @param maxEvaluations          The maximium number of evaluation to perfom on the genetic algorithm
//  * @param populationSize          The population size
//  * @param crossoverOperator       The crossover operator to employ
//  * @param mutationOperator        The mutation operator to employ
//  * @param selectionOperator       The selection operator to employ
//  * @param dominanceComparator     The dominance comparator to employ
//  * @param evaluator               The evaluator use for computing the quality of the individuals
// *  @param filters                 The post-processing filters to be applied after the execution of the EA (by default, only Token Competition)
//  */
//class FEPDS(problem: EPMStreamingProblem,
//                 maxEvaluations: Int = 10000,
//                 populationSize: Int = 50,
//                 crossoverOperator: CrossoverOperator[BinarySolution],
//                 mutationOperator: MutationOperator[BinarySolution],
//                 selectionOperator: SelectionOperator[util.List[BinarySolution], BinarySolution] = new BinaryTournamentSelection[BinarySolution](new RankingAndCrowdingDistanceComparator[BinarySolution]),
//                 dominanceComparator: Comparator[BinarySolution] = new DominanceComparator[BinarySolution]().reversed(),
//                 evaluator: EPMStreamingEvaluator,
//                 filters: Seq[Filter[BinarySolution]] = List(new TokenCompetitionFilter[BinarySolution])
//              )
//  extends NSGAII[BinarySolution](problem,
//    maxEvaluations,
//    populationSize,
//    populationSize,
//    populationSize,
//    crossoverOperator,
//    mutationOperator,
//    selectionOperator,
//    dominanceComparator,
//    evaluator)
//    with EPMStreamingAlgorithm with Serializable {
//
//  require(populationSize % 2 == 0, "Population size must be divisible by 2. Current size: " + populationSize)
//
//  /** The drift detection method */
//  val driftDetector: DriftDetector = new NoDriftDetection(problem)
//
//  /**
//    * ELEMENTS OF THE GENETIC ALGORITHM
//    */
//
//  /** The elite population, where the best patterns found so far are stored */
//  private var elite: util.List[BinarySolution] = new util.ArrayList[BinarySolution]()
//
//  /** A bit set which marks the instances covered on the previous generations. This is for checking the re-init criteria */
//  private var previousCoverage: BitSet = new BitSet(1)
//
//  /** The generation (or evaluation) where the last change in {@code previousCoverage} occurred */
//  private var lastChange = 0
//
//  /** The percentage of generations (or evaluations) without change for triggering the re-initialisation opeartor */
//  private val REINIT_PCT: Float = 0.25f
//
//  /** For oriented initialisation: maximum percentage of variables randomly initialised. */
//  private val PCT_COVERAGE: Double = 0.25
//
//  /** For oriented initialisation: maximum percentage of individuals where only {@code PCT_COVERAGE} of its variables are initialised */
//  private val PCT_INDS_ORIENTED: Double = 0.75
//
//  /** A confidence threshold for filtering purposes. */
//  private val CONFIDENCE_THRESHOLD: Double = 0.6
//
//  /** The timestamp of the data stream we are analysing */
//  private var TIMESTAMP = 0
//
//  /** Class for writting the results extracted into a file */
//  /*var writer = new ResultWriter(
//    "tra",
//    "tst",
//    "tra_summ",
//    "rules",
//    null,
//    problem,
//    evaluator.getObjectives,
//    true)*/
//
//
//  /**
//    * The current class to be processed.
//    */
//  private var CURRENT_CLASS = 0;
//
//
//  /**
//    * The initial population is created by adding the previously extracted individuals
//    *
//    * @return
//    */
//  override def createInitialPopulation(): util.List[BinarySolution] = {
//
//    problem.fixClass(CURRENT_CLASS)
//
//    val newPop: Seq[BinarySolution] = this.problem.getPreviousResultsStreaming() match {
//
//      case Some(model) => {
//        val p = model.filter(ind => ind.getAttribute(classOf[Clase[BinarySolution]]) == CURRENT_CLASS)  // get individuals of the current class
//          .map(ind => {
//            ind.getAttributes.remove(classOf[DominanceRanking[BinarySolution]])  // Remove attributes of the previous run.
//            ind.getAttributes.remove(classOf[CrowdingDistance[BinarySolution]])
//            ind
//          })
//        val numIndividualsToGenerate = getMaxPopulationSize - p.size
//        val generatedIndividuals = (0 until numIndividualsToGenerate).map(x => getProblem.createSolution())
//        (p ++ generatedIndividuals)
//      }
//
//      case None =>  {  // Generate the whole random population
//        (0 until getMaxPopulationSize).map(x => getProblem.createSolution())
//      }
//    }
//
//    newPop.asJava
//  }
//
//
//  /**
//    * Updating the progress in FEPDS is performed by checking the reinitialisation criterion and reinitialising if necessary.
//    * Also, the elite population is updated
//    */
//  override def updateProgress(): Unit = {
//    super.updateProgress() // ONLY NSGA-II (updates the evaluation number)
//
//    JMetalLogger.logger.finest("Evaluations: " + evaluations)
//
//    // Check reinitialisation
//    if (checkReinitialisationCriterion(getPopulation, this.previousCoverage, lastChange, REINIT_PCT)) {
//      JMetalLogger.logger.finest("Reinitialisation at evaluation " + evaluations + " out of " + maxEvaluations)
//      population = coverageBasedInitialisation(population, evaluator.classes(CURRENT_CLASS))
//
//      // evaluate the new generated population to avoid errors
//      evaluator.evaluate(population, problem)
//    }
//  }
//
//  /**
//    * The result is the elite population
//    *
//    * @return
//    */
//  override def getResult: util.List[BinarySolution] = super.getResult
//
//
//  /**
//    * It starts the learning procedure for the extraction of a new pattern set model
//    */
//  def startLearningProcess(classesToRun: Seq[Int]): Unit = {
//    JMetalLogger.logger.fine("Starting " + getName + " execution.")
//    elite = new util.ArrayList[BinarySolution]()
//
//    classesToRun.foreach(clas => {
//      CURRENT_CLASS = clas
//      previousCoverage.clear()
//      lastChange = 0
//      try {
//        super.run() // Run NSGA-II with the overrided methods
//
//        // Return the non-dominated individuals, but first, evalute the population
//        val nonDominatedPopulation = SolutionListUtils.getNondominatedSolutions(population)
//        JMetalLogger.logger.finest("Size Of NonDominatedPopulation: " + nonDominatedPopulation.size())
//
//        // Add to the elite the result of applying the filters on the nonDominatedPopulation
//        var individuals: util.List[BinarySolution] = nonDominatedPopulation
//        for (filter <- filters) {
//          individuals = filter.doFilter(individuals, CURRENT_CLASS, this.evaluator.asInstanceOf[EPMEvaluator])
//        }
//
//        JMetalLogger.logger.info("Size after Filters: " + individuals.size())
//
//        elite.addAll(individuals)
//      } catch {
//        case e: Exception => {
//          // Somethinig went wrong. Notify and return an empty list
//          println("An error has occurred at processing timestamp " + TIMESTAMP + " for class " + clas + ". Please check logs.")
//          JMetalLogger.logger.severe(e.getMessage + ":\n" + e.printStackTrace())
//          elite.addAll(new util.ArrayList[BinarySolution]())
//        }
//      }
//    })
//
//    // At the end, replace the previous results with this one
//    this.problem.replacePreviousResults(elite.asScala, classesToRun)
//    //println("-------------------------")
//    JMetalLogger.logger.fine("Finished " + getName + " execution.")
//
//  }
//
//  override def getName: String = "FEPDS"
//
//
//  override def run(): Unit = {
//    TIMESTAMP += 1
//
//    val toRun = evaluator.classes   // Get the classes with examples in this batch:
//      .map(_.cardinality() > 0)     // Check if there is examples for the class
//      .zipWithIndex                 // Get the index of the class to be executed
//      .filter(_._1)                 // Get only the elements with TRUE values
//      .map(_._2)                    // Get the integer of the class
//
//    // TEST-THEN-TRAIN. First, test the results
//    val model = problem.getPreviousResultsStreaming().asJava
//
//    if (!model.isEmpty) {
//      QualityMeasure.setMeasuresReversed(false) // For test, get the normal values.
//
//      // Get only patterns of the classes with examples in this new batch
//      val filteredModel = model.asScala.filter(toRun contains _.getAttribute(classOf[Clase[BinarySolution]])).asJava
//
//      evaluator.evaluateTest(filteredModel, problem) // Evaluate and enqueue
//      evaluator.enqueue(filteredModel)
//
//      if (!filteredModel.isEmpty) {
//        // write the results in the file (return to expert)
//        writer.setPopulation(filteredModel.asScala)
//        writer.writeStreamingResults(TIMESTAMP, getExecutionTime(), getMemory())
//      }
//    }
//
//    // AFTER TEST, TRAIN
//    // Generate fuzzy sets definitions only once.
//      if(TIMESTAMP <= 1) {
//        JMetalLogger.logger.info("Generating the Fuzzy Sets Definitions at timestamp " + TIMESTAMP)
//        problem.generateFuzzySets()
//      }
//
//    // Drift detection and train if necessary
//    val cl: Seq[Int] = driftDetector.detect(model.asScala, toRun).toSeq
//    if(cl.nonEmpty)
//      startLearningProcess(cl) // In FEPDS, always start the learning process on each batch
//
//  }
//
//
//  /**
//    * It checks whether the population must be reinitialised.
//    *
//    * @param population       The population
//    * @param previousCoverage The coverage of the previous population
//    * @param lastChange       The evaluation where the last change in the population's coverage have been made
//    * @param percentage       The maximum percentage of evaluation to allow no evolution.
//    * @return
//    */
//  def checkReinitialisationCriterion(population: util.List[BinarySolution], previousCoverage: BitSet, lastChange: Int, percentage: Float): Boolean = {
//
//    // Get the current coverage of the whole population
//    val currentCoverage: BitSet = population.asScala.map(ind => ind.getAttribute(classOf[Coverage[BinarySolution]]).asInstanceOf[BitSet]).reduce(_ | _)
//
//    if (previousCoverage == null) {
//      this.previousCoverage = currentCoverage
//      this.lastChange = evaluations
//      return false
//    }
//
//    if (previousCoverage.cardinality() == 0 && currentCoverage.cardinality() != 0) {
//      this.previousCoverage = currentCoverage
//      this.lastChange = evaluations
//      return false
//    }
//    // Calculate if there are new covered examples not previously covered
//    val newCovered: BitSet = (previousCoverage ^ currentCoverage) & (~previousCoverage)
//
//    if (newCovered.cardinality() > 0) {
//      JMetalLogger.logger.finer("New examples covered at evaluation: " + evaluations)
//      this.previousCoverage = currentCoverage
//      this.lastChange = evaluations
//      return false
//    } else {
//      val evalsToReinit = (maxEvaluations * percentage).toInt
//      return (evaluations - lastChange) >= evalsToReinit
//    }
//  }
//
//
//  /**
//    * It performs the coverage-based re-initialisation procedure.
//    *
//    * @param population
//    * @param clase
//    */
//  def coverageBasedInitialisation(population: util.List[BinarySolution], clase: BitSet): util.List[BinarySolution] = {
//    // First of all, perform TOKEN COMPETITION filter
//    val newPopulation = filters(0).doFilter(population, CURRENT_CLASS, evaluator)
//    lastChange = evaluations // iterations
//
//
//    // Then get the coverage of the result and determines the number of new individuals that must be generated
//    val currentCoverage = if (newPopulation.isEmpty) {
//      new BitSet(clase.capacity)
//    } else {
//      newPopulation.asScala.map(ind => ind.getAttribute(classOf[Coverage[BinarySolution]]).asInstanceOf[BitSet]).reduce(_ | _)
//    }
//    previousCoverage = currentCoverage
//
//    // Find a non-covered example of the class
//    val nonCoveredExamples = ~currentCoverage & clase
//
//    if (nonCoveredExamples.cardinality() > 0) {
//      // At least an example of the class is not covered
//      while (nonCoveredExamples.nextSetBit(0) != -1 && newPopulation.size() < populationSize) {
//        val example = nonCoveredExamples.nextSetBit(0)
//        //val pairs = evaluator.getPairsCoveringAnExample(example)
//
//        val newIndividual: BinarySolution = problem.coverageBasedInitialisation(example, evaluator, PCT_COVERAGE)
//        newPopulation.add(newIndividual)
//
//        // Mark the example as covered in order to cover a new one
//        nonCoveredExamples.unset(example)
//      }
//
//      // If there are remainin individuals, generate them randomly
//      if (newPopulation.size() < populationSize) {
//        val newInds = (0 until (populationSize - newPopulation.size())).map(x => getProblem.createSolution())
//        newPopulation.addAll(newInds.asJava)
//      }
//      newPopulation
//    } else {
//      // All examples of the class have been already covered: Random initialisation
//      val newInds = (0 until (populationSize - newPopulation.size())).map(x => getProblem.createSolution())
//      newPopulation.addAll(newInds.asJava)
//      newPopulation
//    }
//
//
//  }
//}
//
