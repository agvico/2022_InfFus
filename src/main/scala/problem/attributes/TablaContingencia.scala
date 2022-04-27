package attributes

import org.uma.jmetal.solution.Solution
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute
import problem.qualitymeasures.ContingencyTable

class TablaContingencia[S <: Solution[_]] extends GenericSolutionAttribute[S, ContingencyTable] {}