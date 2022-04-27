package problem.attributes

import org.uma.jmetal.solution.Solution
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute
import problem.qualitymeasures.WRAccNorm

/**
 * Attribute for measuring the diversity measure
 */
class DiversityMeasure[S <: Solution[_]] extends GenericSolutionAttribute[S, WRAccNorm] {}