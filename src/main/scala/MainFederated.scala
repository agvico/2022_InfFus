import algorithms.{CellDS, SimpleStreamingGeneticAlgorithm}
import genetic.operators.crossover.NPointCrossover
import genetic.operators.mutation.BiasedMutationDNF
import org.uma.jmetal.operator.CrossoverOperator
import org.uma.jmetal.operator.impl.selection.BinaryTournamentSelection
import org.uma.jmetal.solution.BinarySolution
import org.uma.jmetal.util.JMetalLogger
import org.uma.jmetal.util.comparator.RankingAndCrowdingDistanceComparator
import org.uma.jmetal.util.neighborhood.impl.C9
import org.uma.jmetal.util.pseudorandom.JMetalRandom
import picocli.CommandLine
import picocli.CommandLine.Option
import problem._
import problem.evaluator.EPMStreamingEvaluator
import problem.federated._
import problem.filters.Filters
import problem.qualitymeasures.QualityMeasure
import utils.{Files, Utils}

import java.util.logging.Level



class MainFederated extends Runnable with Serializable {

  sealed trait Method   // Enumeration for the algorithm to execute in the devices
  case object CELLDS extends Method
  case object SSGA extends Method

  /*@Parameters(index = "0", paramLabel = "trainingFile", description = Array("The training file in ARFF format."))
  var trainingFile: String = _

  @Parameters(index = "1", paramLabel = "testFile", description = Array("The test file in ARFF format."))
  var testFile: String = _*/

  @Option(names = Array("-s", "--seed"), paramLabel = "SEED", description = Array("The seed for the random number generator. Defaults to 1."))
  var seed: Int = 1

  @Option(names = Array("-h", "--help"), usageHelp = true, description = Array("Show this help message and exit."))
  var help = false

  /*@Option(names = Array("-t", "--training"), paramLabel = "PATH", description = Array("The path for storing the training results file."))
  var resultTraining: String = "tra"

  @Option(names = Array("-T", "--test"), paramLabel = "PATH", description = Array("The path for storing the test results file."))
  var resultTest: String = "test"

  @Option(names = Array("-r", "--rules"), paramLabel = "PATH", description = Array("The path for storing the rules file."))
  var resultRules: String = "rules"*/

  @Option(names = Array("-l", "--labels"), paramLabel = "NUMBER", description = Array("The number of fuzzy linguistic labels for each variable."))
  var numLabels = 3
  require(numLabels >= 2, "At least 2 Linguistic labels must be defined.")

  @Option(names = Array("-Q"), paramLabel = "SIZE", description = Array("The size of the FIFO queue of FEPDS"))
  var QUEUE_SIZE = 0
  require(QUEUE_SIZE >= 0, "Queue size < 0")

  @Option(names = Array("-v"), description = Array("Show INFO messages."))
  var verbose = false

  @Option(names = Array("-c", "--crossover"), description = Array("The crossover probability. By default is 0.6"))
  var CROSSOVER_PROBABILITY: Double = 0.6
  require(CROSSOVER_PROBABILITY >= 0 && CROSSOVER_PROBABILITY <= 1.0, "Crossover probability out of bounds: " + CROSSOVER_PROBABILITY)

  @Option(names = Array("-m", "--mutation"), description = Array("The mutation probability. By default is 0.1"))
  var MUTATION_PROBABILITY: Double = 0.1
  require(MUTATION_PROBABILITY >= 0 && MUTATION_PROBABILITY <= 1.0, "Mutation probability out of bounds: " + MUTATION_PROBABILITY)

 /* @Option(names = Array("-B"), description = Array("Big Data processing using Spark"))
  var bigDataProcessing = false

  @Option(names = Array("-S"), description = Array("Streaming processing"))
  var streamingProcessing = true*/

  @Option(names = Array("-p"), paramLabel = "VALUE", description = Array("Population Size. NOTE: In CellDS it should be a number equals to the size of a n x n grid, e.g., 49 is for 7x7 grids."))
  var POPULATION_SIZE = 49

  @Option(names = Array("-g"), paramLabel = "GENERATOR(s)",split=",", description = Array("A comma-separated list of generators to use. NOTE: Currently, the same generator will be employed on each device, with different instance seed and generation function (if available)."))
  var generatorString = Array("generators.SEAGenerator -b")

  @Option(names = Array("-e"), paramLabel = "VALUE", description = Array("Maximum number of evaluations"))
  var MAX_EVALUATIONS = 5000
  require(MAX_EVALUATIONS >= POPULATION_SIZE, "Evalutions < population size. At least one generation must be performed.")

  @Option(names = Array("-C"), paramLabel = "VALUE", description = Array("Chunk size for non-big data streaming processing"))
  var CHUNK_SIZE = 5000
  require(CHUNK_SIZE > 1, "Chunk size <= 1.")

  /*@Option(names = Array("--time"), paramLabel = "SECONDS", description = Array("Data collect time (in milliseconds) for the Spark Streaming engine"))
  var COLLECT_TIME: Long = 1000

  @Option(names = Array("-n"), paramLabel = "PARTITIONS", description = Array("The number of partitions employed for Big Data"))
  var NUM_PARTITIONS = 8


  @Option(names = Array("--kafkabroker"), paramLabel = "NAME", description = Array("The host an port of the kafka broker being used"))
  var KAFKA_BROKERS = "localhost:9092"*/

  @Option(names = Array("-o", "--objectives"), split=",", paramLabel = "NAME(S)", description = Array("A comma-separated list of quality measures to be used as objectives"))
  var OBJECTIVES = Array("WRAccNorm", "Confidence" )//SuppDiff ) // Probar Confidence: Buenos resultados
  require(OBJECTIVES.length >= 2, "Objectives < 2.")

  /*@Option(names = Array("--topics"), paramLabel = "NAME(S)", description = Array("A comma-separated list of kafka topics to be employed"))
  var TOPICS = Array("test") //Lista de topics separados por coma
  //Kafka params

  //val KAFKA_BROKERS = "kafka-node01:9092"
  @Option(names = Array("--maxSamples"), paramLabel = "Number", description = Array("The maximum number of samples to process before stop the process"))
  var MAX_INSTANCES = -1 //Lista de topics separados por coma*/

  @Option(names = Array("-D", "--numDevices"), paramLabel = "Number", description = Array("The number of devices to simulate in the federated environment."))
  var NUM_DEVICES: Int = 3

  @Option(names = Array("-R", "--learningRounds"), paramLabel = "Number", description = Array("The number of learning rounds to be performed"))
  var MAX_LEARNING_ROUNDS = 40

  @Option(names = Array("--filter"), paramLabel = "NAME", description = Array("The aggregation function in the server. Available filters are: TokenCompetition, Repeated, Measure, CoverageRatio and NonDominated. Default is TokenCompetition."))
  var filter: String = "TokenCompetition"

  @Option(names = Array("--outputDir"), paramLabel = "PATH", description = Array("The path of the directory where to store the result files of each device and server."))
  var outputDir: String = "."

  @Option(names = Array("--no-validation-data"), description = Array("Should validation data be used on the server? By default: yes."))
  var noValidationData: Boolean = false

  @Option(names = Array("--pctAdversaries"), paramLabel = "Number", description = Array("Percentage of adversarial clients. By default, no adversarial clients"))
  var pctAdversarialClients: Double = 0.0

  @Option(names = Array("--attackType"), paramLabel = "Type", description = Array("Type of attack carried out by adversarial: Possible options are: label, feature, falsePatternInjection, patternLabel and patternRemoval"))
  var attackOfAdversarials: String = ""

  //Streams de entrada
  /*val kafkaParams = Map[String, Object](
    "bootstrap.servers" -> KAFKA_BROKERS,
    "key.deserializer" -> classOf[StringDeserializer],
    "value.deserializer" -> classOf[StringDeserializer],
    "num.partitions" -> "1",
    "group.id" -> "SIMIDAT-Group",
    "auto.offset.reset" -> "latest",  //"earliest",  // earliest only for debug purposes
    "enable.auto.commit" -> (false: java.lang.Boolean)
  )*/

  val MAX_EMPTY_BATCHES_TIMEOUT: Int = 10  // Maximum amount of empty batches in a row until considering timeout and finish execution.




  override def run(): Unit = {

    if(help){
      new CommandLine(this).usage(System.err)
      return
    }

    if(verbose) {
      JMetalLogger.logger.setLevel(Level.INFO)
    } else {
      JMetalLogger.logger.setLevel(Level.SEVERE)
    }

    val chunksToProcessPerLearningRound: Int = 20                                          // The number of chunks of data each local device process every learning round
    val numberOfDrifts: Int = 2*MAX_LEARNING_ROUNDS                                        // The number of drifts are twice the number of LR, (two drifts per round)
    val driftEvery: Int = (chunksToProcessPerLearningRound / 2) * CHUNK_SIZE               // Two drifts per LR
    val driftEveryInterval: (Int, Int) = (10 * CHUNK_SIZE, 50 * CHUNK_SIZE)                // Interval where a drift can happen (randomly)


    val rand = JMetalRandom.getInstance()
    rand.setSeed(seed)

    // The problem(s)
    val serverProblem = new EPMStreamingProblem //new EPMSparkStreamingProblem
    serverProblem.setInitialisationMethod(serverProblem.RANDOM_INITIALISATION)
    serverProblem.setRandomGenerator(rand)
    serverProblem.setNumLabels(numLabels)


    // The default crossover, mutation and selection operators to be employed on those algorithm who request it
    val selection = new BinaryTournamentSelection[BinarySolution](new RankingAndCrowdingDistanceComparator[BinarySolution])
    val crossoverProbability = CROSSOVER_PROBABILITY
    val crossover = new NPointCrossover[BinarySolution](crossoverProbability, 2, serverProblem.rand)
    val mutationProbability = MUTATION_PROBABILITY
    val mutation = new BiasedMutationDNF(mutationProbability, serverProblem.rand)

    // The evaluator
    val serverEvaluator = new EPMStreamingEvaluator(QUEUE_SIZE)
    serverEvaluator.setBigDataProcessing(false)
    val objs: Seq[QualityMeasure] = OBJECTIVES.map(Utils.getQualityMeasure)
    serverEvaluator.setObjectives(objs)
    serverProblem.setNumberOfObjectives(objs.length)
    //evaluator.setObjectives(Array(new WRAccNorm, new OddsRatio))


    // Aggregation funciont: By default, remove repeated
    val aggregationFunction: Seq[BinarySolution] => Seq[BinarySolution] = filter.toLowerCase match {
      case "tokencompetition" => Filters.tokenCompetitionFilter(serverEvaluator)
      case "repeated" => Filters.repeatedFilter
      case "measure" => Filters.measureThresholdFilter(Filters.Confidence, 0.6)
      case "coverageratio" => Filters.coverageRatioFilter(serverEvaluator,1)
      case "nondominated" => Filters.nonDominatedFilter
      case _ => {
        JMetalLogger.logger.severe("Incorrect filter selection. Exiting...")
        return
      }
    }



    // Get the attack type for adversarial clients.
    val attackType: AttackType = attackOfAdversarials.toLowerCase match {
      case "label" => LabelDataManipulation
      case "feature" => FeatureDataManipulation
      case "falsepatterninjection" => FalsePatternInjection
      case "patternlabel" => PatternLabelManipulation
      case "patternremoval" => PatternRemovalAttack
      case _ => LabelDataManipulation
    }



    // Create the server
    // Server has a generator with a random function between the available ones and a random seed.
    val serverGenerator =  if(generatorString.length == 1) {
      Utils.createDataGenerator(generatorString(0)) //serverGeneratorString)
    } else {
      val cliString = Utils.getCLIStringForConceptDrift(generatorString, numberOfDrifts, driftEvery,1)
      JMetalLogger.logger.info("SERVER generator: " + cliString)
      Utils.createDataGenerator(cliString)
    }

    // Create the server
    val server: LocalServer = new LocalServer(serverProblem,
      serverEvaluator,
      MAX_LEARNING_ROUNDS,
      aggregationFunction,
      if(noValidationData) None else Some(serverGenerator),
      CHUNK_SIZE,
      outputDir
    )

    // Add the devices

    // Generate each device with its own configuration: own problem, evaluator, generator and algorithm
    // TODO: Maybe devices must be added by means of a JSON ??
    val devices: IndexedSeq[LocalDevice] = for (i <- 0 until NUM_DEVICES) yield {
      val p = new EPMStreamingProblem
      p.setInitialisationMethod(p.RANDOM_INITIALISATION)
      val random = JMetalRandom.getInstance()
      random.setSeed(math.pow(i + 2,3).toInt)
      p.setRandomGenerator(random)
      p.setNumLabels(numLabels)
      p.setNumberOfObjectives(objs.length)
      //p.setNumberOfObjectives(1)

      // The default crossover, mutation and selection operators to be employed on the algorithms algorithm who request it
      val selection = new BinaryTournamentSelection[BinarySolution](new RankingAndCrowdingDistanceComparator[BinarySolution])
      val crossover = new NPointCrossover[BinarySolution](CROSSOVER_PROBABILITY, 2, random)
      val mutation = new BiasedMutationDNF(MUTATION_PROBABILITY, random)

      // The device evaluator
      val e = new EPMStreamingEvaluator(QUEUE_SIZE)
      e.setBigDataProcessing(false)
      val obj = List(objs(i % objs.size))
      e.setObjectives(obj)

      // The data generator: first branch is fon non-concept-drift streams, the second one is for concept-drift streams
      val generatorCLIString: String = if(generatorString.length == 1) {
        createGeneratorString(generatorString(0), i, random)
      } else {
        val driftPoints = Utils.getRandomPoints(driftEveryInterval, numberOfDrifts, random)
        Files.addToFile(outputDir + "/DRIFT_POINTS", driftPoints.map(_.toString).reduceLeft(_ +","+_) + "\n")
        createGeneratorString(Utils.getCLIStringForConceptDriftWithRandomDriftPoint(generatorString, numberOfDrifts, driftPoints),i,random)
      }
      JMetalLogger.logger.info("DEVICE " + i + " generator: " + generatorCLIString)
      val g = Utils.createDataGenerator(generatorCLIString)

      // Learning method: Choose between SSGA or CELLDS
      val method: Method = SSGA
      val algorithm: EPMStreamingAlgorithm = method match {
        case CELLDS => new CellDS(problem = p,
          maxEvaluations = MAX_EVALUATIONS,
          populationSize = POPULATION_SIZE,
          crossoverOperator = crossover.asInstanceOf[CrossoverOperator[BinarySolution]],
          mutationOperator = mutation,
          selectionOperator = selection,
          neighbourhoodType = new C9[BinarySolution](math.sqrt(POPULATION_SIZE).toInt, math.sqrt(POPULATION_SIZE).toInt),
          evaluator = e
        )

        case SSGA => new SimpleStreamingGeneticAlgorithm(
          problem = p,
          evaluator = e,
          selectionOperator = selection,
          crossoverOperator = crossover.asInstanceOf[CrossoverOperator[BinarySolution]],
          mutationOperator = mutation,
          populationSize = POPULATION_SIZE,
          maxEvaluations = MAX_EVALUATIONS,
          topK = 3)
      }

      // Generate a good or an evil device according to the probability with the corresponding parameters.
      if(rand.nextDouble() > pctAdversarialClients){
        LocalDevice(server = server,
          problem = p,
          evaluator = e,
          algorithm = algorithm,
          generator = g,
          CHUNK_SIZE = CHUNK_SIZE,
          outputDir = outputDir)
      } else {
        new EvilLocalDevice(server = server,
          problem = p,
          evaluator = e,
          algorithm = algorithm,
          generator = g,
          CHUNK_SIZE = CHUNK_SIZE,
          outputDir = outputDir,
          attackType = attackType,
          attackProbability = 0.5)
      }

    }

    val numEvilDevices = devices.count {
      case device: EvilLocalDevice => true
      case _ => false
    }
    JMetalLogger.logger.info("Number of adversarial devices: " + numEvilDevices)

    // TODO: Here is were you must start the devices if you need it for asynchronous tasks


    // run the federated learning scheme
    server.run()

    println("Finished")

  }


  /**
   * It adds the -f flag to those instances generators that supports it in order to provide different distributions to the devices.
   *
   *
   * @param generatorString  The string that contains the CLI string of the generator
   * @param id The ID of the device
   * @return
   */
  private def addFunctionToGenerator(generatorString: String, id: Int): String = {

      if (generatorString.contains("SEAGenerator") || generatorString.contains("SineGenerator"))
        "-f " + (id % 4 + 1)
      else if (generatorString contains "AgrawalGenerator")
        "-f " + (id % 10 + 1)
      else if (generatorString contains "AssetNegotiationGeneration")
        "-f " + (id % 5 + 1)
      else if (generatorString contains "MixedGenerator")
        "-f " + (id % 2 + 1)
      else if (generatorString contains "STAGGERGenerator")
        "-f " + (id % 3 + 1)
      else
        ""
  }

  /**
   * It creates the generator CLI string of the given string by means of Adding the -f (function) and -i (seed)
   * arguments dependending on the ID of the device.
   *
   * @param generatorString     The input generator String
   * @param id                  The Device ID.
   * @return
   */
  def createGeneratorString(generatorString: String, id: Int, rand: JMetalRandom): String = {
  val regex = "[.\\w]*Generator".r    // Match anything like generators.AgrawalGenerator

  var counter: Int = -1
   regex.replaceAllIn(generatorString, r => {
     counter += 1
     r.matched + " " + addFunctionToGenerator(r.matched, id + counter) +
                " -i " + math.pow(id + 2, 3).toInt //math.pow(rand.nextInt(0, 50), 3).toInt
  })
}


}

object MainFederated {

  def main(args: Array[String]): Unit = {

    //CommandLine.run(new MainFederated(), System.err, args: _*)

  }

}
