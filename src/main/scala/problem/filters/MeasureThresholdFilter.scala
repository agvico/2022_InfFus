package problem.filters

import org.uma.jmetal.solution.Solution
import problem.evaluator.EPMEvaluator
import problem.qualitymeasures.{ContingencyTable, QualityMeasure}

import java.util
import scala.collection.JavaConverters._

@deprecated("Use 'Filters' instead. ", "14/01/2021")
class MeasureThresholdFilter[S <: Solution[_]](measure: QualityMeasure, threshold: Double) extends  Filter[S]{

  /**
    * It perform the specified filter over the solution list
    *
    * @param solutionList The list of solutions
    * @param clase        The class of the problem if necessary
    * @param evaluator    The information of the evaluation
    * @return A new solution list that passes the filter
    */
  override def doFilter(solutionList: util.List[S], clase: Int, evaluator: EPMEvaluator): util.List[S] = {

    val result = solutionList.asScala.filter(s =>{
      val table: ContingencyTable = s.getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]
      val m: Double = measure.calculateValue(table)
      m < (1.0 - threshold)  // we are MINIMISING !
    })

    if(result.isEmpty){
      val r = solutionList.asScala.sortWith((x,y) => {
        val xtab: ContingencyTable = x.getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]
        val ytab: ContingencyTable = y.getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]
        val measureX: Double = measure.calculateValue(xtab)
        val measureY: Double = measure.calculateValue(ytab)
        measureX < measureY
      })
      List(r.head).asJava // return only the best element if none reaches the threshold
    } else {
    result.asJava
    }
  }
}
