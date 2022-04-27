package attributes

import org.uma.jmetal.solution.Solution
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute
import utils.BitSet

/**
 * It implements the number of elements covered by this individuals
 */
class Coverage[S <: Solution[_]] extends GenericSolutionAttribute[S, BitSet] {}