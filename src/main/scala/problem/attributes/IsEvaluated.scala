package problem.attributes

import org.uma.jmetal.solution.Solution
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute

class IsEvaluated[S <: Solution[_]] extends GenericSolutionAttribute[S, Boolean] {}
