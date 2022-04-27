package problem.federated

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.yahoo.labs.samoa.instances.Instance
import moa.streams.InstanceStream
import org.uma.jmetal.solution.BinarySolution
import problem.EPMStreamingProblem
import problem.evaluator.EPMStreamingEvaluator
import problem.federated.messages._
import utils.{ResultWriter, Utils}

import scala.collection.JavaConverters._

/**
 * A server that represents the central node of the federated, distributed system.
 *
 * It is in charge of collecting the populations from the devices, join together by means of an aggregation and
 * send the results back to the devices if they ask to.
 *
 * Therefore. The server has 3 clearly differentiated behaviors:
 * 1. collect results from devices: it receives the patterns from devices and updates its state by aggregating the
 *    received patterns to the current set of patterns (updates its state). Sends an ACK message to the sender after that.
 * 2. send the current pattern model (or state) to the device if they ask to.
 * 3. to grant permission to new devices that want to be added into the federated network.
 *
 * On the other hand, the device will also have 4 behaviours:
 * 1. send their patterns to the server
 * 2. ask the server for a new model
 * 3. compute the new model from its local data (no interaction but a change in its internal state by means of the EA)
 * 4. ask for being added in the network.
 */
object AkkaServer {

  val validationInstances: Int = 500000
  type AggregationFunc = Seq[BinarySolution] => Seq[BinarySolution]

  def apply(problem: EPMStreamingProblem,
            evaluator: EPMStreamingEvaluator,
            aggregationFunction: AggregationFunc,
            validationData: Option[InstanceStream],
            outputDir: String = "."): Behavior[DeviceCommand] = {

    val valData = validationData match {  // If there is validation data available, start the problem and evaluator
      case Some(value) => {
        val data = for(i <- 0 until validationInstances) yield value.nextInstance().getData
        Some(data)
      }
      case None => None
    }

    val evalOpt = valData match {
      case Some(data) => {
        problem.readDataset(data, validationData.get.getHeader)
        problem.generateFuzzySets()
        evaluator.initialise(problem)
        Some(evaluator)
      }
      case None => None
    }

    state(Vector.empty, 0, 0, aggregationFunction, valData, evalOpt, outputDir + "/SERVER")
  }


  /**
   * It contains the logic of the actor. It contains the actions for the message and the current state of the actor
   * @param model          The current global patterns model
   * @param deviceList      The list of devices currently running within the network
   * @param f             The aggregation function employed.
   * @return
   */
  private def state(model: Seq[BinarySolution],
                    round: Int,
                    numDevices: Int,
                    f: AggregationFunc,
                    validationData: Option[Seq[Instance]],
                    evaluator: Option[EPMStreamingEvaluator],
                    outputDir: String): Behavior[DeviceCommand] = {
    Behaviors.receive((ctx, message) => {

      message match {
          /**
           * SendPatterns is the key message. The device has sent their best patterns to the device.
           * Now, the server is in charge to aggregate them to the current state and send them back to the device
           * */
        case SendPatterns(patterns, from) => {
         // ctx.log.info("Received {} patterns from: {}", patterns.size, from)
          val t_ini = System.currentTimeMillis()

          // First of all, if we have validation data, we have to validate the received model
          val evaluatedPatterns: Seq[BinarySolution] = evaluator match {
            case Some(eval) => eval.evaluateTest(patterns.asJava, eval.getProblem).asScala
            case None => {patterns}
          }

          // Then, aggregate the results with respect to all patterns
          val aggregationResult = f(model ++ evaluatedPatterns)
          val execTime = System.currentTimeMillis() - t_ini
          //ctx.log.debug("Server has now: " + aggregationResult.size + " patterns.")

          // TODO: Write the results to disk
         // writeResults(outputDir, Some(aggregationResult), evaluator.get, round, execTime ,0)

          // At the end, send the result back to the responder device
          from ! ResponseWithPatterns(Utils.cleanPatternAttributes(aggregationResult))
          state(aggregationResult, round, numDevices, f, validationData, evaluator, outputDir)
        }

        case WRITE_RESULTS(from) => {
          // TODO: Write the results to disk
          writeResults(outputDir, Some(model), evaluator.get, round, 0 ,0)
          state(model, round + 1, numDevices, f, validationData, evaluator, outputDir)
        }

        case AskForPatterns(from) => {  // A device (from) have asked for patterns, send them
          from ! ResponseWithPatterns(model)
          Behaviors.same
        }

        case JoinToNetwork(from) => {   // Add to federated network request
          from ! SERVER_ACK(from.toString + " added to the network")
          from ! DoComputation(ctx.self)   // After join, ask to start the computation
          val nDevs = numDevices + 1
          ctx.log.debug("Number of devices in server: {}", nDevs)
          state(model, round, nDevs, f, validationData, evaluator, outputDir)  // The new states includes the new device
        }

        case DEVICE_ACK(message) => {  // Confirmation received
          ctx.log.info("ACK received: {}", message)
          Behaviors.same
        }

        case DEVICE_ERROR(message) => {  // Error received
          ctx.log.error("ERROR received: {}", message)
          Behaviors.same
        }

        // A device has correctly finished its execution:
        // Remove it from the list of device. If it is the last device in the list. Stop the server.
        case COMPUTATION_DONE(from) => {
          ctx.log.info("{} has correctly finished its execution.", from)
          ctx.log.debug("Number of devices in server: {}", numDevices - 1)
          if(numDevices - 1 == 0){
            ctx.log.info("No active devices. Stopping Server.")
            Behaviors.stopped
          } else {
            state(model, round, numDevices - 1, f, validationData, evaluator, outputDir)
          }
          //Behaviors.same
        }
      }
    })
  }


  /**
   * It writes the test results stored in the model.
   * @note The model must be previously tested using validation data!
   * @param path
   * @param model
   * @param round
   * @param avgExecTime
   * @param memory
   */
  private def writeResults(path: String, model: Option[Seq[BinarySolution]], evaluator: EPMStreamingEvaluator, round: Int, avgExecTime: Long, memory: Long): Unit = {

    model match {
      case Some(m) => {
        //ResultWriter.writeStreamingRules(path + "_rules", m, round)
        ResultWriter.writeStreamingSummaryTestResults(path + "_tst_summ", m, evaluator, round, avgExecTime, 0.0)
      }
      case None => {}
    }

  }

}
