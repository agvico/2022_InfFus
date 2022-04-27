package problem.qualitymeasures

/**
 * It represents a Linear combination of quality measures with some weights (sum of weights = 1)
 */
@deprecated("Switchin to more functional programming-based approach", "14/01/2021")
class LinearCombination(measures: Seq[QualityMeasure], weights: Seq[Double]) extends QualityMeasure {
  assert(measures.length == weights.length, "Measures and weights vectors do not have the same length")
  assert(weights.sum >= 0.99 && weights.sum <= 1.01, "Sum of weights != 1")

  val measuresAndWeights = measures.zip(weights)
  name = "LinearCombination"
  short_name = "LinearComb"

  /**
   * It calculates the value of the given quality measure by means of the
   * given contingency table
   *
   * @param t
   * @return
   */
  override protected def calculate(t: ContingencyTable): Double = {
    table = t
    value =  measuresAndWeights.map(q => q._1.calculate(t) * q._2).sum
    value
  }

  /**
   * It returns the inverse value of the measure.
   *
   * @return the inverse value (or NaN otherwise)
   */
  override protected def inverse(): Double = 1 - value

  /**
   * It checks that the value of the measure is within the domain of the
   * measure
   *
   * @return
   */
  override def validate(): Unit = {}

  override def compareTo(o: QualityMeasure): Int = value.compareTo(o.value)

  override def clone(): QualityMeasure = new LinearCombination(measures, weights)
}
