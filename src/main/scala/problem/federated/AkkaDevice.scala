package problem.federated

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.yahoo.labs.samoa.instances.Instance
import moa.streams.InstanceStream
import org.uma.jmetal.solution.BinarySolution
import problem.conceptdrift.{DriftDetector, MeasureBasedDriftDetector}
import problem.evaluator.EPMStreamingEvaluator
import problem.federated.messages._
import problem.qualitymeasures._
import problem.{EPMStreamingAlgorithm, EPMStreamingProblem}
import utils.Utils

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * La idea es que el dispositivo:
 * 1. ejecute de primeras, envíe reglas al server que las fusiona.
 * 2. Continua testeando su propio modelo hasta que la calidad decaiga.
 * 3. Cuando decaiga, reentrena y el resultado se envía al server para que se agregue.
 * 4. El server responde con el modelo global agregado que se emplea
 */
object AkkaDevice {

  def apply(serverRef: ActorRef[DeviceCommand],
            problem: EPMStreamingProblem,
            evaluator: EPMStreamingEvaluator,
            algorithm: EPMStreamingAlgorithm,
            generator: InstanceStream,
            filter: Seq[BinarySolution] => Seq[BinarySolution],
            CHUNK_SIZE: Int = 5000,
            maxChunks: Int = 100,
            outputDir: String = "."): Behavior[ServerCommand] = {

    val measureThreshold: Double = evaluator.getObjectives.head match {
      case x: LinearCombination => 0.6
      case x: WRAccNorm => 0.55
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
        case q: QualityMeasure => "CONF" //q.getShortName
      },
      0.6)

  // Initial state
    state(
      serverRef,
      BigInt(0),
      Vector.empty,
      problem,
      evaluator,
      algorithm,
      driftDetector,
      filter,
      generator,
      CHUNK_SIZE,
      maxChunks,
      outputDir
    )
  }

  /**
   * It defines the state and the behaviours of the Device:
   * @param serverRef            The reference to the server (where messages are sent to)
   * @param numState             The number of data blocks processed (for the stopping condition and result analysis)
   * @param localModel            The current pattern model stored in the device
   * @param problem              The EPM problem defined in the device
   * @param evaluator            The evaluator
   * @param algorithm             The Streaming evaluator in the device
   * @param driftDetector         The conceptDrift detector
   * @param generator             The data generator
   * @param chunkSize             The chunk size
   * @param outputDir             The dir to save the results
   * @return
   */
  private def state(serverRef: ActorRef[DeviceCommand],
                    numState: BigInt,
                    localModel: Seq[BinarySolution],
                    problem: EPMStreamingProblem,
                    evaluator: EPMStreamingEvaluator,
                    algorithm: EPMStreamingAlgorithm,
                    driftDetector: DriftDetector,
                    filter: Seq[BinarySolution] => Seq[BinarySolution],
                    generator: InstanceStream,
                    chunkSize: Int,
                    maxChunks: Int,
                    outputDir: String): Behavior[ServerCommand] = {

  Behaviors.setup { ctx =>
    Behaviors.supervise(
      Behaviors.receiveMessage[ServerCommand] {

        /**
         * Main method. It collects the data from the stream and execute the streaming algorithm.
         */
        case DoComputation(replyTo) => {
          //replyTo ! DEVICE_ACK( ctx.self.toString +  ": Starting learning process.") // Send ACK to the server
          val dataSeq: Seq[Instance] = collectDataFromStream(generator, chunkSize) // collect the data from the stream

          val t_ini = System.currentTimeMillis() // initialise structures (problem and evaluators)
          problem.readDataset(dataSeq, generator.getHeader)
          if (numState == 0) {
            problem.generateFuzzySets()
          }
          evaluator.initialise(problem)

          // Run the method (Following test-then-train)
          val testedModel = algorithm.test(problem, evaluator)(localModel) // test
          val toRun = driftDetector.detect(testedModel, evaluator.classes.indices) // detect drift
          val executed: Seq[BinarySolution] = if (toRun.nonEmpty) { // run (if drift is detected)
            // We need to run the method as a drift has been detected.
            algorithm.train(problem, evaluator)(testedModel)
          } else {
            Vector.empty
          }

          // After the method, extract execution time statistics.
          val t_end = System.currentTimeMillis()
          val currExecTime = t_end - t_ini
          val memory: Double = (Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory()) / (1024.0 * 1024.0)
          val newModel = algorithm.getResult.asScala
          algorithm.setExecutionTime(currExecTime)
          algorithm.setMemory(memory)

          // After the learning method, send the patterns to the server if it is required.
          if(numState == 0){
            replyTo ! SendPatterns(Utils.cleanPatternAttributes(newModel), ctx.self)
          } else {
            if(executed.nonEmpty){
              // Send patterns to the server (we have re-executed)
              replyTo ! SendPatterns(Utils.cleanPatternAttributes(newModel), ctx.self)
            }
          }

          // Independently of the previous command, ask the server to save the results
          replyTo ! WRITE_RESULTS(ctx.self)

          // Next, continue the computation of the arriving data if we do not reach the stopping condition.
          if(numState < maxChunks){
            ctx.self ! DoComputation(replyTo)  // Continue the computation (go to next state)
            state(replyTo, numState + 1, newModel, problem, evaluator, algorithm, driftDetector, filter, generator, chunkSize, maxChunks, outputDir)
          } else {
            replyTo ! COMPUTATION_DONE(ctx.self)  // Stop the computation
            Behaviors.stopped
          }

        }


        /**
         * Behavior carried out when the server sends the aggregated model
         */
        case ResponseWithPatterns(patterns) => {
          // When patterns are received from the server after asking them. Mix both local and remote patterns
          ctx.log.info("{} received {} patterns from the server.", ctx.self, patterns.size)
          // But first, test and filter the received patterns
          val newPatterns = evaluator.evaluateTest(patterns.asJava, problem).asScala
          val p = filter(newPatterns)

          val newModel = localModel ++ p

          // Then, in order to keep only the best received patterns with respect to the current data. Filter them
          // by confidence
          //val tModel = evaluator.evaluateTest(newModel.asJava, problem)
          //val finalModel = Filters.measureThresholdFilter(Filters.Confidence, 0.6)(tModel.asScala)

          // Next, replace the results
          problem.replacePreviousResults(newModel)

          // End. Go to new state.
          state(serverRef, numState, newModel, problem, evaluator, algorithm, driftDetector, filter, generator, chunkSize, maxChunks, outputDir)
        }


        /**
         * Other messages
         */
        case SERVER_ACK(message) => {
          //ctx.log.info("ACK from Server: {}", message)
          Behaviors.same
        }

        case SERVER_ERROR(message) => {
          //ctx.log.error("ERROR from Server: {}", message)
          Behaviors.same
        }
      }

    ).onFailure[RuntimeException]({
      SupervisorStrategy.restart
    })
  }

  }



  private def collectDataFromStream(generator: InstanceStream, numSamples: Int): IndexedSeq[Instance] = {
    val dataSeq: ArrayBuffer[Instance] = new ArrayBuffer[Instance]()

    while (dataSeq.size < numSamples && generator.hasMoreInstances) { // Collect chunk of data
      dataSeq += generator.nextInstance().getData
    }

    dataSeq
  }




}
