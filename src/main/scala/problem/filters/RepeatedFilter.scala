package problem.filters

import org.uma.jmetal.solution.Solution
import problem.evaluator.EPMEvaluator

import java.util

/**
  * It removes repeated patterns.
  *
  * @tparam S An individual (a pattern)
  * @author Ángel Miguel García Vico (agvico@ujaen.es)
  */
@deprecated("Use 'Filters' instead. ", "14/01/2021")
class RepeatedFilter[S <: Solution[_]] extends  Filter[S]{

  override def doFilter(solutionList: util.List[S], clase: Int, evaluator: EPMEvaluator): util.List[S] = {
    val marcas = new Array[Boolean](solutionList.size())
    val numVars = solutionList.get(0).getNumberOfVariables

    // Mark repeated values
    for(i <-  0 until solutionList.size()){
      for(j <- (i + 1) until solutionList.size()){
        var equal = true
        var k = 0
        while(k < numVars && equal){
          if(!solutionList.get(i).getVariableValue(k).equals(solutionList.get(j).getVariableValue(k))){
            equal = false
          }
          k += 1
        }
        if(equal) marcas(i) = true
      }
    }

    // save non-repeated ones
    val toRet = new util.ArrayList[S]()
    for( i <- marcas.indices){
      if(!marcas(i)) toRet.add(solutionList.get(i))
    }

    // Return
    toRet
  }



}
