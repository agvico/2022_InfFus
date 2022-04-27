package problem.federated

import moa.streams.InstanceStream

import scala.collection.mutable.ArrayBuffer


/**
 * Abstract class that represents a server which contains several Devices D which generate a model M that is aggregated.
 *
 * @param maxLearningRounds               The maximum learning rounds to run the server
 * @param aggregationFunction             The aggregation function to use: from a Sequence of models, returns an aggregated one.
 * @param validationData                  The optional validation data to use
 * @param VALIDATION_DATA_CHUNK_SIZE      The chunk size for validation data
 * @param fileDir                         The file where result files must be stored
 * @tparam T          The model type
 */
@deprecated abstract class Server[T](
                      maxLearningRounds: Int,
                      aggregationFunction: T => T,
                      validationData: Option[InstanceStream],
                      val VALIDATION_DATA_CHUNK_SIZE: Int = 5000,
                     val fileDir: String = ".")
  extends Runnable
    with Serializable {


  /**
   * The list of devices
   */
  protected val devices: ArrayBuffer[Device[T]] = ArrayBuffer[Device[T]]()

  protected var globalModel: Option[T] = None

  protected val timeout: Long = 10000

  /**
   * It tells the server that device '''d''' is ready to receive a learning task
   *
   * @param d The device to be added
   */
  def addAvailableDevice(d: Device[T]): Unit = synchronized {
    devices += d
  }

  /**
   * It r
   * @param d
   */
  def removeAvailableDevice(d: Device[T]): Unit = synchronized {
    val ind: Int = devices.indexOf(d)
    if(ind >= 0) devices.remove(ind)
  }


  /**
   * It selects a subset of the available devices for performing a new learning task
   *
   * @param devices
   * @param timeout
   * @return   The selected devices
   */
  def selectDevices(devices: Seq[Device[T]], timeout: Long): Seq[Device[T]]


  /**
   * It send A COPY of the global model to the selected devices for the learning task.  Note that the process may fail on some devices
   * @param devices
   * @param timeout
   * @return
   */
  def updateLocalModels(devices: Seq[Device[T]], timeout: Long): Seq[Device[T]]


  /**
   * It runs the learning algorithm of each device.  Note that the process may fail on some devices
   *
   * @param devices
   * @param timeout
   * @return  The updated model on each device
   */
  def executeLearningTaskOnDevices(devices: Seq[Device[T]], timeout: Long): Seq[T]


  /**
   * it validates the models using the (optional) validation data streams
   * @param data     The (optional) data stream to employ for validation
   * @param models    The model to evaluate
   * @return         The evaluated models.
   */
  def validateModels(data: InstanceStream, models: Seq[T]): Seq[T]

  /**
   * It writes the results to a file.
   *
   * @param path
   * @param model
   * @param round
   */
  def writeResults(path: String, model: Option[T], round: Int, avgExecTime: Long, memory: Long): Unit


  def run(): Unit
}
