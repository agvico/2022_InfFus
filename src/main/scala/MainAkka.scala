
import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import algorithms.{CellDS, SimpleStreamingGeneticAlgorithm}
import com.typesafe.config.ConfigFactory
import genetic.operators.crossover.NPointCrossover
import genetic.operators.mutation.BiasedMutationDNF
import org.uma.jmetal.operator.CrossoverOperator
import org.uma.jmetal.operator.impl.selection.BinaryTournamentSelection
import org.uma.jmetal.solution.BinarySolution
import org.uma.jmetal.util.comparator.{ObjectiveComparator, RankingAndCrowdingDistanceComparator}
import org.uma.jmetal.util.neighborhood.impl.C9
import org.uma.jmetal.util.pseudorandom.JMetalRandom
import picocli.CommandLine
import picocli.CommandLine.Option
import problem.evaluator.EPMStreamingEvaluator
import problem.federated._
import problem.federated.messages.JoinToNetwork
import problem.filters.Filters
import problem.qualitymeasures.QualityMeasure
import problem.{EPMStreamingAlgorithm, EPMStreamingProblem}
import utils.{Files, ResultWriter, Utils}

import java.util.concurrent.Callable
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class MainAkka extends Callable[Int] {

sealed trait Method   // Enumeration for the algorithm to execute in the devices
case object CELLDS extends Method
case object SSGA extends Method

@Option(names = Array("-s", "--seed"), paramLabel = "SEED", description = Array("The seed for the random number generator. Defaults to 1."))
var seed: Int = 1

@Option(names = Array("-h", "--help"), usageHelp = true, description = Array("Show this help message and exit."))
var help = false

@Option(names = Array("-l", "--labels"), paramLabel = "NUMBER", description = Array("The number of fuzzy linguistic labels for each variable."))
var numLabels: Int = 3
require(numLabels >= 2, "At least 2 Linguistic labels must be defined.")

//@Option(names = Array("-Q"), paramLabel = "SIZE", description = Array("The size of the FIFO queue of FEPDS"))
val QUEUE_SIZE: Int = 0  // TODO: Remove this as we are not using this yet
require(QUEUE_SIZE >= 0, "Queue size < 0")

@Option(names = Array("-v"), description = Array("Show INFO messages."))
var verbose = false

@Option(names = Array("-c", "--crossover"), description = Array("The crossover probability. By default is 0.6"))
var CROSSOVER_PROBABILITY: Double = 0.6
require(CROSSOVER_PROBABILITY >= 0 && CROSSOVER_PROBABILITY <= 1.0, "Crossover probability out of bounds: " + CROSSOVER_PROBABILITY)

@Option(names = Array("-m", "--mutation"), description = Array("The mutation probability. By default is 0.1"))
var MUTATION_PROBABILITY: Double = 0.1
require(MUTATION_PROBABILITY >= 0 && MUTATION_PROBABILITY <= 1.0, "Mutation probability out of bounds: " + MUTATION_PROBABILITY)

@Option(names = Array("-p"), paramLabel = "VALUE", description = Array("Population Size."))
var POPULATION_SIZE: Int = 50

@Option(names = Array("-g"), paramLabel = "GENERATOR(s)",split=",", description = Array("A comma-separated list of generators to use. NOTE: Currently, the same generator will be employed on each device, with different instance seed and generation function (if available)."))
var generatorString: Array[String] = Array("generators.SEAGenerator -b")

@Option(names = Array("-e"), paramLabel = "VALUE", description = Array("Maximum number of evaluations within the evolutionary process."))
var MAX_EVALUATIONS: Int = 5000
require(MAX_EVALUATIONS >= POPULATION_SIZE, "Evalutions < population size. At least one generation must be performed.")

@Option(names = Array("-C"), paramLabel = "VALUE", description = Array("Chunk size for non-big data streaming processing"))
var CHUNK_SIZE = 5000
require(CHUNK_SIZE > 1, "Chunk size <= 1.")

@Option(names = Array("-o", "--objectives"), split=",", paramLabel = "NAME(S)", description = Array("A comma-separated list of quality measures to be used as objectives"))
var OBJECTIVES: Array[String] = Array("LinearCombination:WRAccNorm;SuppDiff;Confidence")
//require(OBJECTIVES.length >= 2, "Objectives < 2.")


@Option(names = Array("-D", "--numDevices"), paramLabel = "Number", description = Array("The number of devices to simulate in the federated environment."))
var NUM_DEVICES: Int = 3

@Option(names = Array("-R", "--learningRounds"),  paramLabel = "Number", description = Array("The number of learning rounds to be performed"))
var MAX_LEARNING_ROUNDS = 40

@Option(names = Array("--filter"), split=",",paramLabel = "NAME", description = Array("The aggregation function(s) in the server. Available filters are: TokenCompetition, Repeated, Measure, CoverageRatio and NonDominated. Default is TokenCompetition. For several filters, use a comma-separated list of values in the order they are applied."))
var filter: Array[String] = Array("Confidence", "TokenCompetition")

@Option(names = Array("--outputDir"), paramLabel = "PATH", description = Array("The path of the directory where to store the result files of each device and server."))
var outputDir: String = "."

@Option(names = Array("--no-validation-data"), description = Array("Should validation data be used on the server? By default: yes."))
var noValidationData: Boolean = false

@Option(names = Array("--pctAdversaries"), paramLabel = "Number", description = Array("Percentage of adversarial clients. By default, no adversarial clients"))
var pctAdversarialClients: Double = 0.0

@Option(names = Array("--attackType"), paramLabel = "Type", description = Array("Type of attack carried out by adversarial: Possible options are: label, feature, falsePatternInjection, patternLabel and patternRemoval"))
var attackOfAdversarials: String = ""


val MAX_EMPTY_BATCHES_TIMEOUT: Int = 10  // Maximum amount of empty batches in a row until considering timeout and finish execution.


 override def call(): Int = {

  if(help){
    new CommandLine(this).usage(System.err)
    return CommandLine.ExitCode.OK
  }

   // Starts the main actor system (Guardian).
   // This is in charge of spawning and configuring the server and the devices.
   val system = ActorSystem(StartActorSystem(),"Guardian", ConfigFactory.load())

   // The guardian waits here until the system finished (TAKE CARE OF INFINITE RUNS!)
   Await.result(system.whenTerminated, Duration.Inf)   // TODO: Debe haber una mejor forma de detener esto.

   // END
    CommandLine.ExitCode.OK
}


  object StartActorSystem {
    def apply(): Behavior[NotUsed] = {
      Behaviors.setup{ context =>
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


        // Aggregation function: By means of Function.chain we can concatenate aggregation functions one after another

        val aggregationFunction: Seq[BinarySolution] => Seq[BinarySolution] = Function.chain(
          filter.map(i => {
            val a: Seq[BinarySolution] => Seq[BinarySolution] = i.toLowerCase  match {
              case "tokencompetition" => Filters.tokenCompetitionFilter(serverEvaluator)
              case "repeated" => Filters.repeatedFilter
              case "confidence" => Filters.measureThresholdFilter( Filters.Confidence, 0.6)
              case "coverageratio" => Filters.coverageRatioFilter(serverEvaluator,0.9)
              case "nondominated" => Filters.nonDominatedFilter
              case _ => {
                //JMetalLogger.logger.severe("Incorrect filter selection. Exiting...")
                context.log.error("Incorrect filter selection. Exiting...")
                return Behaviors.stopped
              }
            }
            a
          })
        )




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
          //JMetalLogger.logger.info("SERVER generator: " + cliString)
          //context.log.info("SERVER generator: {}", cliString)
          Utils.createDataGenerator(cliString)
        }

        // Create the server
        val server = context.spawn(AkkaServer(
          serverProblem,
          serverEvaluator,
          aggregationFunction,
          Some(serverGenerator),
          outputDir),
          "Server")

        context.watch(server)  // TODO: Watch termination

        /*val server: LocalServer = new LocalServer(serverProblem,
          serverEvaluator,
          MAX_LEARNING_ROUNDS,
          aggregationFunction,
          if(noValidationData) None else Some(serverGenerator),
          CHUNK_SIZE,
          outputDir
        )*/

        // Add the devices

        // Generate each device with its own configuration: own problem, evaluator, generator and algorithm
        // TODO: Maybe devices must be added by means of a JSON ??
        val devices = for (i <- 0 until NUM_DEVICES) yield {
          val p = new EPMStreamingProblem
          p.setInitialisationMethod(p.RANDOM_INITIALISATION)
          val random = JMetalRandom.getInstance()
          random.setSeed(math.pow(i + 2,3).toInt)
          p.setRandomGenerator(random)
          p.setNumLabels(numLabels)
          p.setNumberOfObjectives(objs.length)

          // The default crossover, mutation and selection operators to be employed on the algorithms algorithm who request it
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
          //JMetalLogger.logger.info("DEVICE " + i + " generator: " + generatorCLIString)
          //context.log.info("DEVICE {} generator: {}", i , generatorCLIString)
          val g = Utils.createDataGenerator(generatorCLIString)

          // Learning method: Choose between SSGA or CELLDS
          val method: Method = SSGA
          val algorithm: EPMStreamingAlgorithm = method match {
            case CELLDS => new CellDS(problem = p,
              maxEvaluations = MAX_EVALUATIONS,
              populationSize = POPULATION_SIZE,
              crossoverOperator = crossover.asInstanceOf[CrossoverOperator[BinarySolution]],
              mutationOperator = mutation,
              selectionOperator = new BinaryTournamentSelection[BinarySolution](new RankingAndCrowdingDistanceComparator[BinarySolution]),
              neighbourhoodType = new C9[BinarySolution](math.sqrt(POPULATION_SIZE).toInt, math.sqrt(POPULATION_SIZE).toInt),
              evaluator = e
            )

            case SSGA => new SimpleStreamingGeneticAlgorithm(
              problem = p,
              evaluator = e,
              selectionOperator = new BinaryTournamentSelection[BinarySolution](new ObjectiveComparator[BinarySolution](0, ObjectiveComparator.Ordering.DESCENDING)),
              crossoverOperator = crossover.asInstanceOf[CrossoverOperator[BinarySolution]],
              mutationOperator = mutation,
              populationSize = POPULATION_SIZE,
              maxEvaluations = MAX_EVALUATIONS,
              topK = 3)
          }

          // Generate the result writer of each device:
          val filePrefix: String = outputDir + "/" + g.getClass.getSimpleName + "__" + "Device_" + f"$i%03.0f" + "_"
          val resultWriter: ResultWriter = new ResultWriter(filePrefix + "tra",
            filePrefix + "tst",
            filePrefix + "tstSumm",
            filePrefix + "rules",
            List.empty,
            p,
            e.getObjectives,
            true
          )
          algorithm.setResultWriter(resultWriter)

          // Generate a good or an evil device according to the probability with the corresponding parameters.
          context.spawn(AkkaDevice(server,
            p,
            e,
            algorithm,
            g,
            Filters.measureThresholdFilter(Filters.Confidence, 0.6),  // Adds confidence filter to the Device
            CHUNK_SIZE,
            MAX_LEARNING_ROUNDS,
            outputDir),
          "DEVICE_" + i)
        }

        // The devices ask the server to join the network.
        // After that, the server will send the signal to start the evaluation.
        devices.foreach(server ! JoinToNetwork(_))

        Behaviors.receiveSignal {
          case (_, Terminated(_)) =>
            Behaviors.stopped
        }

        /*val numEvilDevices = devices.count {
          case device: EvilLocalDevice => true
          case _ => false
        }
        JMetalLogger.logger.info("Number of adversarial devices: " + numEvilDevices)*/
      }
    }
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

object MainAkka {

  // TODO: apply() method will start the ActorSystem and the federated system.

  def main(args: Array[String]): Unit = {
    System.exit(new CommandLine(new MainAkka()).execute(args: _*))
  }

}

