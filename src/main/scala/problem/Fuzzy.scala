package problem

import net.sourceforge.jFuzzyLogic.membership.{MembershipFunction, MembershipFunctionTriangular, Value}

import scala.collection.mutable.ArrayBuffer


/** Fuzzy membership functions generation object */
object Fuzzy {


  /**
   * It automatically generated an uniform partition with the specified number of linguistic labels
   * @param min
   * @param max
   * @param numLabels
   * @return
   */
  def generateTriangularLinguisticLabels(min: Double, max: Double, numLabels: Int): Seq[MembershipFunction] = {
    val marca = (max - min) / (numLabels - 1).toDouble
    var cutPoint = min + marca / 2
    val variables = new ArrayBuffer[MembershipFunction]()

    for(label <- 0 until numLabels){
      val definitions = new ArrayBuffer[Double]
      var value = min + marca * (label - 1)
      // Creation of x0 point
      if (label == 0)
        definitions += min - marca
      else
        definitions += Round(value, max)

      // Creation of x1 point
      value = min + marca * label
      definitions += Round(value, max)

      // Creation of x2 point
      value = min + marca * (label + 1)
      if (label == numLabels - 1)
        definitions += max + marca
      else
        definitions += Round(value, max)

      // Add the linguistic term to the variable

      val fuzSet = new MembershipFunctionTriangular(new Value(definitions(0)), new Value(definitions(1)), new Value(definitions(2)))
      fuzSet.estimateUniverse()
      variables += fuzSet
        //new MembershipFunctionTriangular(definitions(0), definitions(1), definitions(2))

      cutPoint += marca
    }

    // return an inmutable version of the array
    variables
  }


  private  def Round(value: Double, tope: Double): Double = {
    if (value > -0.0001 && value < 0.0001) return 0
    if (value > tope - 0.0001 && value < tope + 0.0001) return tope
    value
  }

}