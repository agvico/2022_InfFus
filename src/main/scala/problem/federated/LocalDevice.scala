package problem.federated

import com.yahoo.labs.samoa.instances.Instance
import moa.streams.InstanceStream
import org.uma.jmetal.solution.BinarySolution
import problem.evaluator.EPMStreamingEvaluator
import problem.{EPMStreamingAlgorithm, EPMStreamingProblem}
import utils.{ResultWriter, Utils}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * Class that represents a device which generates its own data
 *
 * @param problem       The EPM problem
 * @param algorithm     Optional: The algorithm to extract knowledge. Servers do not have a data mining algorithm
 * @param generator     The data generator
 */
@deprecated case class LocalDevice(override val server: LocalServer, //Server[Seq[BinarySolution], Device[Seq[BinarySolution]]],
                       val problem: EPMStreamingProblem,
                       val evaluator: EPMStreamingEvaluator,
                       override val algorithm: EPMStreamingAlgorithm,
                       override val generator: InstanceStream,
                       val CHUNK_SIZE: Int = 5000,
                       val maxChunksToProcess: Int = 20,
                       val outputDir: String = ".")
  extends Device[Seq[BinarySolution]](server, algorithm, generator, outputDir)
    with Cloneable
{
  server.addAvailableDevice(this)  // inmediately adds the device to the server.

  var firstbatch = true
  var timestamp = 0
  val execTimes: ArrayBuffer[Long] = new ArrayBuffer[Long]()
  val resultWriter: ResultWriter = new ResultWriter(filePrefix + "tra",
    filePrefix + "tst",
    filePrefix + "tstSumm",
    filePrefix + "rules",
    List.empty,
    problem,
    evaluator.getObjectives,
    true
  )

algorithm.setResultWriter(resultWriter)

  // TODO: Asynchronous devices must perform in the call() method:
  // 1) Generate data batches
  // 2) Test the model with this new batch
  // 3) If learning signal is received, then learn. Otherwise go to step 1.

  override def call(): Seq[BinarySolution] = {

    var iterations = 0
    execTimes.clear()

    while (iterations < maxChunksToProcess) {

      val dataSeq: Seq[Instance] = collectResultsFromStream(generator, CHUNK_SIZE)
      val currPatterns = runLearningMethod(algorithm, dataSeq)

      iterations += 1
    }

    getResult

  }


  override def collectResultsFromStream(generator: InstanceStream, numSamples: Int): IndexedSeq[Instance] = {
    val dataSeq: ArrayBuffer[Instance] = new ArrayBuffer[Instance]()

    while (dataSeq.size < numSamples && generator.hasMoreInstances) { // Collect chunk of data
      dataSeq += generator.nextInstance().getData
    }

    dataSeq
  }


  /**
   * It executes the learning method with the given sequence of instances as data. It returns a set of patterns.
   *
   * @param algorithm
   * @param data
   * @return
   */
  override def runLearningMethod(algorithm: EPMStreamingAlgorithm, data: Seq[Instance]): Seq[BinarySolution] = {

    val t_ini = System.currentTimeMillis()
    problem.readDataset(data, generator.getHeader)
    if (firstbatch) {
      problem.generateFuzzySets()
      firstbatch = false
    }
    evaluator.initialise(problem)

    // Run the mehod
    algorithm.run()

    val t_end = System.currentTimeMillis()
    val currExecTime = t_end - t_ini
    execTimes += currExecTime
    val memory: Double = (Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory()) / (1024.0 * 1024.0)
    algorithm.setExecutionTime(currExecTime)
    algorithm.setMemory(memory)

    algorithm.getResult.asScala
  }

  override def getResult: Seq[BinarySolution] = algorithm.getResult.asScala




  /**
   * It checks whether the device is ready for a learning task or not.
   *
   * @note On Local Devices, it always returns @code{this}
   * @return an Option with @code{this} is available, None otherwise
   */
  override def isAvailableForLearning: Option[LocalDevice] = Some(this)

  override def getAverageExecutionTime: Long = Utils.mean(execTimes).toLong

  override def getTotalExecutionTime: Long = execTimes.sum
}
