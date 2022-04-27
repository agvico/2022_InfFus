package attributes

import org.uma.jmetal.operator.Operator
import org.uma.jmetal.solution.Solution
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute

class GeneratedOperator[S <: Solution[_]] extends GenericSolutionAttribute[S, Operator[_, _]] {}