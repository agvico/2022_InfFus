package problem.qualitymeasures


class OddsRatioInterval {
  private val z_alfa_2 = 1.96

  var min: Double = 0.0
  var max: Double = 0.0

  def calculate(contingencyTable: ContingencyTable): (Double, Double) = {
    val tp = if (contingencyTable.getTp == 0) 0 else 1.0 / contingencyTable.getTp.toDouble
    val fp = if (contingencyTable.getFp == 0) 0 else 1.0 / contingencyTable.getFp.toDouble
    val tn = if (contingencyTable.getTn == 0) 0 else 1.0 / contingencyTable.getTn.toDouble
    val fn = if (contingencyTable.getFn == 0) 0 else 1.0 / contingencyTable.getFn.toDouble

    val odds = new OddsRatio().calculate(contingencyTable)
    val w = z_alfa_2 * math.sqrt(tp + fp + tn + fn)

    this.min = odds * math.exp(-w)
    this.max = odds * math.exp(w)
    (this.min, this.max)
  }

  /**
    * It checks whether this overlaps with other odds ratio interval
    *
    * @param other
    * @return
    */
  def overlap(other: OddsRatioInterval): Boolean = {

    val thisMax = this.max > other.max

    if (thisMax) {
      this.min <= other.max
    } else {
      other.min <= this.max
    }
  }
}
