package problem.federated

import com.yahoo.labs.samoa.instances.Instance
import moa.streams.InstanceStream
import org.uma.jmetal.solution.BinarySolution
import problem.EPMStreamingAlgorithm
import problem.federated.Device.generateID

import java.util.concurrent.Callable


/**
 * Class that represents a device in the federated learning environment that returns a model of type T.
 *
 * @param server
 * @param algorithm
 * @param generator
 * @param filesDirectory
 * @tparam T
 */
@deprecated abstract class Device[T](val server: Server[T],
                      val algorithm: EPMStreamingAlgorithm,
                      val generator: InstanceStream,
                     val filesDirectory: String = ".")
extends Callable[T] with Serializable
{

  val ID = generateID
  val filePrefix: String = filesDirectory + "/" + generator.getClass.getSimpleName + "__" + "Device_" + f"$ID%03.0f" + "_"

  def getResult: T
  def getAverageExecutionTime: Long
  def getTotalExecutionTime: Long


  /**
   * It checks whether the device is ready for a learning task or not.
   *
   * @note On Local Devices, it always returns @code{this}
   * @return an Option with @code{this} is available, None otherwise
   */
  def isAvailableForLearning: Option[Device[T]]

  /**
   * It is in charge of collect the instances that arrives from the stream.
   *
   * It return the selected number of instances as a sequence of instances.
   *
   * @param generator
   * @param numSamples
   * @return
   */
  def collectResultsFromStream(generator: InstanceStream, numSamples: Int): IndexedSeq[Instance]


  /**
   * It executes the learning method with the given sequence of instances as data. It returns a set of patterns.
   *
   * @param algorithm
   * @param data
   * @return
   */
  def runLearningMethod(algorithm: EPMStreamingAlgorithm, data: Seq[Instance]): Seq[BinarySolution]

}

object Device {
  private var id = 0

  /**
    * It generates a new ID for a given device
    * @return
    */
  def generateID: Int = synchronized {
    id += 1
    id
  }
}