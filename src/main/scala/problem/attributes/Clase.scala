package problem.attributes

import org.uma.jmetal.solution.Solution
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute


/**
 * It implements the class attribute for an algorithm
 */
class Clase[S <: Solution[_]] extends GenericSolutionAttribute[S, Integer] {}
