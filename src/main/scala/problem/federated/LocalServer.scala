package problem.federated


import com.yahoo.labs.samoa.instances.Instance
import genetic.individual.EPMBinarySolution
import moa.streams.InstanceStream
import org.uma.jmetal.solution.BinarySolution
import org.uma.jmetal.util.JMetalLogger
import problem.EPMStreamingProblem
import problem.evaluator.EPMStreamingEvaluator
import problem.qualitymeasures.QualityMeasure
import utils.{ResultWriter, Utils}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
 * Class that represents a Server node on the Federated configuration.
 *
 * Local configuration: Server execute the devices in parallel in the same local machine.
 *
 * @note THE FEDERATED LEARNING IS PERFORMED LOCALLY
 *
 * @param problem       The EPM problem
 * @param validationData The validation data generator (is necessary)
 */
@deprecated class LocalServer(problem: EPMStreamingProblem,
                  evaluator: EPMStreamingEvaluator,
                  maxLearningRounds: Int,
                  aggregationFunction: Seq[BinarySolution] => Seq[BinarySolution],
                  validationData: Option[InstanceStream],
                 validationDataChunkSize: Int = 5000,
                 outputDir: String = ".")
  extends Server[Seq[BinarySolution]] (maxLearningRounds,aggregationFunction, validationData, validationDataChunkSize, outputDir) {

  // Flag for first model validation
  var firstValidation = true


  lazy val data: Seq[Instance] = validationData match {
    case Some(value) => for (i <- 0 until 1000000) yield value.nextInstance().getData
    case None => Seq.empty
  }

  /**
   * It selects a subset of the available devices for performing a new learning task
   *
   * @param devices
   * @param timeout
   * @return The selected devices
   */
  override def selectDevices(devices: Seq[Device[Seq[BinarySolution]]], timeout: Long): Seq[Device[Seq[BinarySolution]]] = {
    devices
  }


  /**
   * It send A COPY of the global model to the selected devices for the learning task.  Note that the process may fail on some devices
   *
   * @param devices
   * @param timeout
   * @return
   */
  override def updateLocalModels(devices: Seq[Device[Seq[BinarySolution]]], timeout: Long): Seq[Device[Seq[BinarySolution]]] = {
    globalModel match {
      case Some(value) => {
        devices.foreach {
          case ld: LocalDevice => {
            //ld.problem.replacePreviousResults(
            ld.problem.addResultsToPrevious(
              globalModel.get.map(_.copy().asInstanceOf[EPMBinarySolution]) // CREATE A COPY OF THE Global model and send. This is to avoid concurrency errors
            )
          }
        }
        devices
      }

      case None => devices
    }
  }

  /**
   * it validates the model using the (optional) validation data streams
   *
   * @param data  The (optional) data stream to employ for validation
   * @param models The model to evaluate
   * @return The evaluated model.
   */
  override def validateModels(data: InstanceStream, models: Seq[Seq[BinarySolution]]): Seq[Seq[BinarySolution]] = {

    //val d = for (i <- 0 until VALIDATION_DATA_CHUNK_SIZE) yield data.nextInstance().getData // Generate the validation data from the validation stream
    if(problem.getData == null)
      problem.readDataset(this.data, data.getHeader)

    if (firstValidation) {
      problem.generateFuzzySets()
      firstValidation = false
    } // TODO: Maybe we must fix this as this is not the correct way...

    evaluator.initialise(problem)
    models.map(m => evaluator.evaluateTest(m.asJava, problem).asScala)
  }


  /**
   * It runs the learning algorithm of each device.  Note that the process may fail on some devices
   *
   * @param devices
   * @param timeout
   * @return The updated model on each device
   */
  override def executeLearningTaskOnDevices(devices: Seq[Device[Seq[BinarySolution]]], timeout: Long): Seq[Seq[BinarySolution]] = {
    devices.par.flatMap {
      case d: LocalDevice => {
        Try(d.call()) match {
          case Success(value) => Some(value)
          case Failure(exception) => {
            // Exception: Save information and return None
            JMetalLogger.logger.severe("ERROR ON DEVICE ID: " + d.ID)
            exception.printStackTrace()
            None
          }
        }
      }
    }.seq
  }

  /**
   * It writes the results to a file.
   *
   * @param path
   * @param model
   * @param round
   */
  override def writeResults(path: String, model: Option[Seq[BinarySolution]], round: Int, avgExecTime: Long, memory: Long): Unit = {

    model match {
      case Some(m) => {
        ResultWriter.writeStreamingRules(path + "_rules", m, round)
        //ResultWriter.writeStreamingSummaryTestResults(path + "_tst_summ", m, round, avgExecTime, 0.0)
      }
      case None => {}
    }

  }


  override def run(): Unit = {
    var rounds = 0
    while(rounds < maxLearningRounds) {

      println("Round: " + rounds)

      // Select a subset of the available devices
      // Note that in FL we have from thousands to millions of devices
      val selectedDevices = selectDevices(devices, timeout)

      // Next, update the local model of the available devices with the current global one
      val updatedDevices = updateLocalModels(selectedDevices, timeout)

      // Run each available local device in parallel and retrieve the local models
      QualityMeasure.setMeasuresReversed(true)
      val collectedModel: Seq[Seq[BinarySolution]] = executeLearningTaskOnDevices(updatedDevices, timeout)

      // Test the arriving patterns with validation data if available

      // TODO: Make sure before this that validation is a blocking operation (i.e., quality measures must be reversed on Devices while testing uses regular values)
      QualityMeasure.setMeasuresReversed(false)
      val evaluatedModel: Seq[Seq[BinarySolution]] = validationData match {
        case Some(data) => {
          // Optional: Validate the aggregated model with the validation data data
          validateModels(data, collectedModel)
        }
        case None => { collectedModel}   // Do nothing (return the current model)
      }

      //  Perform the fusion method (default: remove repeated)
      val aggregation_t_ini: Long = System.currentTimeMillis()

      globalModel = Some(aggregationFunction( evaluatedModel.reduce(_++_) ))

      val aggregation_t_end: Long = System.currentTimeMillis()
      val aggregationTime: Long = aggregation_t_end - aggregation_t_ini


      val avgTimes: Long = Utils.mean(devices.map(_.getTotalExecutionTime)).toLong
      writeResults(fileDir + "/" + "SERVER", globalModel, rounds, avgTimes, 0)

      QualityMeasure.setMeasuresReversed(true)
      rounds += 1
    }
  }

}





