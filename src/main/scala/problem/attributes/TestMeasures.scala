package attributes

import org.uma.jmetal.solution.Solution
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute
import problem.qualitymeasures.QualityMeasure

class TestMeasures[S <: Solution[_]] extends GenericSolutionAttribute[S, Seq[QualityMeasure]] {}