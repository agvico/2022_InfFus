package genetic.operators.selection

import org.uma.jmetal.algorithm.multiobjective.nsgaiii.util.{EnvironmentalSelection, ReferencePoint}
import org.uma.jmetal.solution.Solution

import java.util.List
import java.{lang, util}

/**
  * Wrapper for NSGA-III with errors fixed
  */
class EnvironmentalSelectionFix[S <: Solution[_]](fronts: util.List[util.List[S]],
                                                  solutionsToSelect: Int,
                                                  referencePoints: util.List[ReferencePoint[S]],
                                                  numberOfObjectives: Int)
  extends EnvironmentalSelection[S](fronts, solutionsToSelect, referencePoints, numberOfObjectives) {

  override def perpendicularDistance(direction: util.List[lang.Double], point: util.List[lang.Double]): Double = {
    var numerator: lang.Double = 0.0
    var denominator: lang.Double = 0.0
    for (i <- 0 until direction.size()) {
      numerator += direction.get(i) * point.get(i)
      denominator += Math.pow(direction.get(i), 2.0)
    }

    val k: lang.Double = if (denominator != 0)
      numerator / denominator
    else
      lang.Double.MAX_VALUE - 1.0

    var d: lang.Double = 0.0

    for (i <- 0 until direction.size()) {
      d += Math.pow(k * direction.get(i) - point.get(i), 2.0)
    }
    Math.sqrt(d)
  }


  override def associate(population: util.List[S]): Unit = {
    for (t <- 0 until fronts.size()) {
      for (i <- 0 until fronts.get(t).size()) {
        val s: S = fronts.get(t).get(i)
        var min_rp = 0;
        var min_dist = Double.MaxValue
        for (r <- 0 until referencePoints.size()) {
          val d = perpendicularDistance(this.referencePoints.get(r).position, getAttribute(s).asInstanceOf[List[lang.Double]]);
          if (d < min_dist) {
            min_dist = d;
            min_rp = r;
          }
        }
        if (t + 1 != fronts.size()) {
          this.referencePoints.get(min_rp).AddMember();
        } else {
          this.referencePoints.get(min_rp).AddPotentialMember(s, min_dist);
        }
      }
    }
  }

}
