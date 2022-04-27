package problem

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import problem.attributes.Attribute


/**
  * Class for representing an EPM problem tackled by means of Spark Streaming
  */
class EPMSparkStreamingProblem extends EPMStreamingProblem {
  def setAttributes(atts: Seq[Attribute]) = attributes = atts


  /**
    * RDD of Seq[Double] for Spark Streaming which contains the index of a given example and the arriving data from the stream
    */
  protected var dataRDD: RDD[(Seq[Double], Long)] = _


  def getRDD(): RDD[(Seq[Double], Long)] = dataRDD


  /**
    * Converts an RDD from Spark Streaming to a DataFrame
    *
    * @param data
    */
  def readDatasetSparkStreaming(data: RDD[Seq[Double]], numPartitions: Int, calculateBounds: Boolean): Unit = {

    this.numPartitions = numPartitions
    this.spark = SparkSession.active

    val t_ini = System.currentTimeMillis()
    dataRDD = data.zipWithUniqueId().cache()

    if (calculateBounds)
      calculateAttributesBounds()

    numExamples = dataRDD.count().toInt
    val execTime = System.currentTimeMillis() - t_ini
    //println("Time to read dataset: " + execTime + " ms.  NumExamples: " + numExamples)
  }


  /**
    * It calculates the minimum and maximum values for each attribute in the RDD
    */
  def calculateAttributesBounds(): Unit = {
    val numAttribs = attributes.length

    // Calculate the minimum and maximum for each attribute
    val boundaries = dataRDD.mapPartitions(partition => {
      val bounds = Array.fill(numAttribs)((Double.MaxValue, Double.MinValue))
      partition.foreach(element => {
        val data = element._1
        for (i <- data.indices) {
          if (data(i) < bounds(i)._1) bounds(i) = (data(i), bounds(i)._2) // Replace min
          if (data(i) > bounds(i)._2) bounds(i) = (bounds(i)._1, data(i)) // Replace max
        }
      })
      Array(bounds).iterator
    }
    ).reduce((x, y) => {
      val bounds = Array.fill(numAttribs)((Double.MaxValue, Double.MinValue))
      for (i <- 0 until numAttribs) {
        bounds(i) = (Math.min(x(i)._1, y(i)._1), Math.max(x(i)._2, y(i)._2))
      }
      bounds
    })

    // Set the attributes
    this.attributes = boundaries.indices.map(i => {
      if (attributes(i).isNominal)
        new Attribute(attributes(i).name, true, 0, 0, this.numLabels, attributes(i).nominalValue)
      else
        new Attribute(attributes(i).name, false, boundaries(i)._1, boundaries(i)._2, this.numLabels, null)
    })

  }

}
