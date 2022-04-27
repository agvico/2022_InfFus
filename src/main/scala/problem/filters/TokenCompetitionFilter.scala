package problem.filters

import attributes.Coverage
import org.uma.jmetal.solution.{BinarySolution, Solution}
import problem.attributes.{Clase, DiversityMeasure}
import problem.evaluator.EPMEvaluator
import problem.qualitymeasures.WRAccNorm
import utils.BitSet

import java.util
import scala.collection.mutable.ArrayBuffer


@deprecated("Use 'Filters' instead. ", "14/01/2021")
class TokenCompetitionFilter[S <: Solution[_]] extends Filter[S]{


  def doFilter(solutionList: util.List[S], clase: Int, evaluator: EPMEvaluator): util.List[S] ={
    val result = new util.ArrayList[S]()
    val pop = new ArrayBuffer[S]()

    for(i <- 0 until solutionList.size()){
      val ind = solutionList.get(i)
      if(ind.getAttribute(classOf[Clase[S]]).asInstanceOf[Int] == clase){
        pop += ind
      }
    }

    // Sort first by quality measure, in case of tie, sort by number of active variables.
    val orderPop = pop.sortWith(sortIndividuals)
    var tokens = new BitSet(orderPop(0).getAttribute(classOf[Coverage[S]]).asInstanceOf[BitSet].capacity)

    var counter = 0
    var allCovered = false

    do{

      val coverage = orderPop(counter).getAttribute(classOf[Coverage[S]]).asInstanceOf[BitSet]
      val clase = orderPop(counter).getAttribute(classOf[Clase[S]]).asInstanceOf[Int]

      // covered examples that belongs to the class
      val correct = coverage & evaluator.classes(clase)

      val newCov = (tokens ^ correct) & (~tokens)
      if(newCov.cardinality() > 0){
        result.add(orderPop(counter))
      }
      tokens = tokens | correct

      // As we are covering only tokens of the class. All covered must be set when all examples of the given class are also covered
      if(tokens.cardinality() == tokens.capacity || (tokens & evaluator.classes(clase)).cardinality() == evaluator.classes(clase).cardinality() ){
        allCovered = true
      }
      counter += 1

    } while(counter < orderPop.size && !allCovered)

    if(result.isEmpty){
      result.add(orderPop(0))
    }

    result
  }



  /**
    * Sorts the individuals according to the value of the diversity measure.
    * In case of tie, it selects the one with less active elements.
    *
    * @param x
    * @param y
    * @return
    */
  private def sortIndividuals(x: S, y: S): Boolean ={

      val quaX = x.getAttribute(classOf[DiversityMeasure[S]]).asInstanceOf[WRAccNorm].getValue
      val quaY = y.getAttribute(classOf[DiversityMeasure[S]]).asInstanceOf[WRAccNorm].getValue

      if (quaX > quaY) {
        true
      } else if (quaX < quaY) {
        false
      } else {
        var longX = 0
        var longY = 0

        for (i <- 0 until x.getNumberOfVariables) {
          if (x.asInstanceOf[BinarySolution].getVariableValue(i).cardinality() > 0 && x.asInstanceOf[BinarySolution].getVariableValue(i).cardinality() < x.asInstanceOf[BinarySolution].getNumberOfBits(i))
            longX += x.asInstanceOf[BinarySolution].getVariableValue(i).cardinality()

          if (y.asInstanceOf[BinarySolution].getVariableValue(i).cardinality() > 0 && y.asInstanceOf[BinarySolution].getVariableValue(i).cardinality() < y.asInstanceOf[BinarySolution].getNumberOfBits(i))
            longY += y.asInstanceOf[BinarySolution].getVariableValue(i).cardinality()
        }

        longX < longY
      }



  }

}
