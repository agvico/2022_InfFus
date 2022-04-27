package problem.federated

import com.yahoo.labs.samoa.instances.Instance
import moa.streams.InstanceStream
import problem.evaluator.EPMStreamingEvaluator
import problem.{EPMStreamingAlgorithm, EPMStreamingProblem}

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

sealed trait AttackType
  case object LabelDataManipulation extends AttackType
  case object FeatureDataManipulation extends AttackType
  case object PatternLabelManipulation extends AttackType
  case object FalsePatternInjection extends AttackType
  case object PatternRemovalAttack extends AttackType




/**
 * Class for representing a malign local device that tampers with the input data they collect in order to
 * corrupt the learning method.
 *
 * @param server
 * @param problem       The EPM problem
 * @param evaluator
 * @param algorithm     Optional: The algorithm to extract knowledge. Servers do not have a data mining algorithm
 * @param generator     The data generator
 * @param CHUNK_SIZE
 * @param outputDir
 */
@deprecated("Use AkkaDevice instead", "14/01/2021")
class EvilLocalDevice(override val server: LocalServer, //Server[Seq[BinarySolution], Device[Seq[BinarySolution]]],
                      override val problem: EPMStreamingProblem,
                      override val evaluator: EPMStreamingEvaluator,
                      override val algorithm: EPMStreamingAlgorithm,
                      override val generator: InstanceStream,
                      override val maxChunksToProcess: Int = 20,
                      override val CHUNK_SIZE: Int = 5000,
                      override val outputDir: String = ".",
                      val attackType: AttackType,
                      val attackProbability: Double = 0.5)
extends LocalDevice(server, problem, evaluator, algorithm,generator, CHUNK_SIZE, maxChunksToProcess, outputDir) {

  val randomGenerator = new Random()

  // This evil local device tampers with the data by randomly changing the labels of the class
  override def collectResultsFromStream(generator: InstanceStream, numSamples: Int): IndexedSeq[Instance] = {
    val dataSet: ArrayBuffer[Instance] = new ArrayBuffer[Instance]()

    while(dataSet.size < numSamples && generator.hasMoreInstances){
      val instance = if(randomGenerator.nextDouble() > attackProbability){
                        generator.nextInstance().getData
                      } else {
                        val auxInst = generator.nextInstance().getData
                        manipulateData(attackType, auxInst, randomGenerator)
                      }
      dataSet += instance
    }
    dataSet

  }



  /**
   * It alters the given input instance.
   *
   * @param inst
   * @param randomSource
   * @return
   */
  def manipulateData(attackType: AttackType, inst: Instance, randomSource: Random): Instance = {

    attackType match {
      case LabelDataManipulation => {
        val numClasses = inst.numClasses()
        if(numClasses == 2)
          inst.setClassValue( (inst.classValue()+1) % 2)
        else
          inst.setClassValue(randomGenerator.nextInt(numClasses))

        inst
      }

      case FeatureDataManipulation =>  inst  // TODO
      case PatternLabelManipulation => inst
      case FalsePatternInjection => inst
      case PatternRemovalAttack => inst
    }

  }
}
