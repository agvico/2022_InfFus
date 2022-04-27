package problem.conceptdrift

import attributes.TestMeasures
import org.uma.jmetal.solution.BinarySolution
import problem.EPMStreamingProblem
import problem.attributes.Clase
import problem.qualitymeasures.QualityMeasure
import utils.Utils

import scala.collection.mutable


/**
  * Drift detection based on a measure using a confidence interval for the mean value
  *
  * @param measure
  * @param measureThreshold
  */
class MeasureBasedDriftDetector(problem: EPMStreamingProblem,
                                measure: String,
                                measureThreshold: Double
                               )
  extends DriftDetector(problem) {

  val nClasses: Int = 100
  var currentConfidenceIntervals: Array[Option[(Double, Double)]] = Array.fill[Option[(Double, Double)]](nClasses)(None)


  /**
    * It detects real drift by means of looking at some quality measures of the model.
    *
    * @param model
    * @return It returns a set of ints representing those classes which have suffered concept drift
    */
  override def detect(model: Seq[BinarySolution], batchClasses: Seq[Int]): Iterable[Int] = {
    if(model.isEmpty) {
      batchClasses
    } else {
      model.groupBy(
        _.getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int] // Group individuals by class
      ).flatMap(group => {

        val measures: Seq[Double] = if (QualityMeasure.areInversed) {
          group._2.map( // Get confidence of each individual in the group
            _.getAttribute(classOf[TestMeasures[BinarySolution]])
              .asInstanceOf[mutable.ArraySeq[QualityMeasure]]
              .filter(_.getShort_name equals (measure))
              .head
              .getValueInversed
          )
        } else {
          group._2.map( // Get confidence of each individual in the group
            _.getAttribute(classOf[TestMeasures[BinarySolution]])
              .asInstanceOf[mutable.ArraySeq[QualityMeasure]]
              .filter(_.getShort_name equals (measure))
              .head
              .getValue
          )
        }

        // Calculate average and confidence interval
        //val confidenceInterval = Utils.meanConfidenceInterval(measures, confidenceLevel)
        val avgMeasure = Utils.mean(measures)

        val clazz = group._1

        // If the average measure is below the threshold, then mark the class to be re-executed
        if (avgMeasure < measureThreshold) Some(clazz) else None

      })
    }
  }
}
