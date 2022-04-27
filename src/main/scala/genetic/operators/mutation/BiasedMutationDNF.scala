package genetic.operators.mutation

import org.uma.jmetal.operator.MutationOperator
import org.uma.jmetal.solution.BinarySolution
import org.uma.jmetal.util.pseudorandom.JMetalRandom

class BiasedMutationDNF(
                         /**
                         * The mutation probability
                         */
                        var mutationProb: Double,

                        /**
                         * The random number generator. It must be consistent with the whole problem
                         */
                        var rand: JMetalRandom)

  extends MutationOperator[BinarySolution] {


  override def execute(binarySolution: BinarySolution) = doMutation(binarySolution)


  private def doMutation(binarySolution: BinarySolution) = {

    val sol = binarySolution.copy.asInstanceOf[BinarySolution]
    if (rand.nextDouble(0.0, 1.0) <= mutationProb) {
      val variable = rand.nextInt(0, sol.getNumberOfVariables - 1)
      if (rand.nextDouble(0.0, 1.0) < 0.5) {
        // remove variable
        sol.getVariableValue(variable).clear()
      } else {
        // random flip of variables
        for(i <- 0 until sol.getNumberOfBits(variable)) {
          if (rand.nextDouble(0.0, 1.0) < 0.5) {
            sol.getVariableValue(variable).flip(i)
          }
        }
      }
    }

    sol
  }


}