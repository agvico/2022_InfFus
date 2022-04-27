package problem

import com.yahoo.labs.samoa.instances._
import org.uma.jmetal.solution.BinarySolution
import problem.attributes.Clase

import java.util



/**
  * Class for representing EPM problems under Data Stream Mining Environments
  */
class EPMStreamingProblem extends EPMProblem {


  /**
    * For Streaming data, the results extracted previous if they are necessary
    */
  protected var previousResultsStreaming: Seq[BinarySolution] = new Array[BinarySolution](0)



  def getPreviousResultsStreaming(): Option[Seq[BinarySolution]] = {
    if(previousResultsStreaming.isEmpty)
      None
      else
      Some(previousResultsStreaming)
  }

  /**
    * Append some results to the set of previously extracted results
    * @param results
    */
  def addResultsToPrevious(results: Seq[BinarySolution]): Unit= {
    previousResultsStreaming = previousResultsStreaming ++ results
  }

  /**
    * It replaces the set of current previously extracted results with a new one
    * @param results
    */
  def replacePreviousResults(results: Seq[BinarySolution]): Unit = previousResultsStreaming = results

  /**
    * It only deletes individuals of the given class and add the results
    *
    * @param results
    */
  def replacePreviousResults(results: Seq[BinarySolution], classes: Seq[Int]): Unit = {
    previousResultsStreaming = previousResultsStreaming.filter(i => !classes.contains(i.getAttribute(classOf[Clase[BinarySolution]]))) ++ results
  }


  /**
    * It converts a set of MOA instances using the ARFF Stream into a dataset that can be employed in EPM.
    *
    * @param instances  A Seq of MOA instances
    * @param header The instance header information. It can be extracted from the ARFFStream class
    */
  def readDataset(instances: Seq[Instance], header: InstancesHeader): Unit ={
    val converter = new SamoaToWekaInstanceConverter

    val attributes: util.ArrayList[Attribute] = new util.ArrayList[Attribute]()

    for (i <- 0 until header.numInputAttributes()){
      attributes.add(header.inputAttribute(i))
    }
    attributes.add(header.classAttribute())

    val inst = new Instances(header.getRelationName, attributes, instances.length)
    instances.foreach(instance => inst.add(instance))
    inst.setClassIndex(attributes.size() - 1)
    data = converter.wekaInstances(inst)

    data.setClassIndex(data.numAttributes - 1)

    // Get the attributes information
    this.attributes = generateAttributes()

    // Generate the fuzzy sets (if necessary)
    //generateFuzzySets()

    numExamples = data.numInstances()


  }


  // TODO: Create a copy constructor

}
