package problem.filters

import attributes.Coverage
import org.uma.jmetal.solution.{BinarySolution, Solution}
import problem.evaluator.EPMEvaluator
import problem.qualitymeasures.{ContingencyTable, OddsRatioInterval}
import utils.BitSet

import java.util

/**
  * It performs the coverage ratio filter proposed in:
  *
  * Li, J., Liu, J., Toivonen, H., Satou, K., Sun, Y., Sun, B.: Discovering statistically
  * non-redundant subgroups. Knowl.-Based Syst. 67, 315–327 (2014)
  *
  * First, it checks if two patterns covers have at least a given % of covered instances in common.
  * Next, it determines if the OddsRatioIntervals of both patterns overlaps. If they do, they are redundant. The pattern
  * with more variables is removed.
  *
  * @param maxCoveragePercent The % of instances in common
  * @tparam S An individual
  * @author Ángel Miguel García Vico (agvico@ujaen.es)
  */
@deprecated("Use 'Filters' instead. ", "14/01/2021")
class CoverageRatioFilter[S <: Solution[_]](maxCoveragePercent: Double) extends Filter[S] {

  val z_alpha_2 = 1.96

  /**
    * It perform the specified filter over the solution list
    *
    * @param solutionList The list of solutions
    * @param clase        The class of the problem if necessary
    * @param evaluator    The information of the evaluation
    * @return A new solution list that passes the filter
    */
  override def doFilter(solutionList: util.List[S], clase: Int, evaluator: EPMEvaluator): util.List[S] = {

    if(solutionList.size() < 2) return solutionList

 var toRemove = new BitSet(solutionList.size())

    for (i <- 0 until solutionList.size()) {
      var cover = solutionList.get(i).getAttribute(classOf[Coverage[S]]).asInstanceOf[BitSet]
      if(cover.cardinality() <= 0){
        toRemove.set(i)
      }
      if(!toRemove.get(i)) {
        for (j <- (i + 1) until solutionList.size()) {
          // Directly remove is the solution does not cover any example
          var cover = solutionList.get(j).getAttribute(classOf[Coverage[S]]).asInstanceOf[BitSet]
          if (cover.cardinality() <= 0) {
            toRemove.set(j)
          }
          if (!toRemove.get(j)) {
            val P1: BitSet = solutionList.get(i).getAttribute(classOf[Coverage[S]]).asInstanceOf[BitSet]
            val P2: BitSet = solutionList.get(j).getAttribute(classOf[Coverage[S]]).asInstanceOf[BitSet]

            val P1_pos: BitSet = P1 & evaluator.classes(clase)
            val P2_pos: BitSet = P2 & evaluator.classes(clase)

            val intersection: Double = (P1_pos & P2_pos).cardinality()
            val sizeP1: Double = P1_pos.cardinality()
            val sizeP2: Double = P2_pos.cardinality()

            val overlapPct = Math.max(intersection / sizeP1, intersection / sizeP2)

            if (overlapPct >= maxCoveragePercent) { // If one subsume another, keep the one with the best odds ratio
              val contingencyTableP1 = solutionList.get(i).getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]
              val contingencyTableP2 = solutionList.get(j).getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]

              val oddsIntervalP1 = new OddsRatioInterval()
              oddsIntervalP1.calculate(contingencyTableP1)

              val oddsIntervalP2 = new OddsRatioInterval()
              oddsIntervalP2.calculate(contingencyTableP2)

              if (oddsIntervalP1.min > oddsIntervalP2.max) {
                // P2 is worse, remove it
                toRemove.set(j)
              } else if (oddsIntervalP2.min > oddsIntervalP1.max) {
                // P1 is worse, remove it
                toRemove.set(i)
              } else if (oddsIntervalP1.overlap(oddsIntervalP2)) {
                // if the overlap, we the difference between them are not significant, remove the one with the highest number of variables
                if (getNumVars(solutionList.get(i).asInstanceOf[BinarySolution]) <= getNumVars(solutionList.get(j).asInstanceOf[BinarySolution])) {
                  toRemove.set(j)
                } else {
                  toRemove.set(i)
                }
              }


            }
          }
        }
      }
    }

    // Remove elements with the flag
    val newSolutionList = new util.ArrayList[S]()
    for (i <- 0 until solutionList.size()) {
      if (!toRemove.get(i)) {
        newSolutionList.add(solutionList.get(i))
      }
    }

    if(newSolutionList.isEmpty) {  // Exceptional case: Any pattern covers an example.
      newSolutionList.add(solutionList.get(0))
    }
    newSolutionList
  }


  def getNumVars(sol: BinarySolution) = {
    var vars = 0
    for (i <- 0 until sol.getNumberOfVariables) {
      if (sol.getVariableValue(i).cardinality > 0 && sol.getVariableValue(i).cardinality < sol.getNumberOfBits(i))
        vars += sol.getVariableValue(i).cardinality
    }
    vars
  }

}
