package problem.filters

import attributes.Coverage
import org.uma.jmetal.solution.impl.DefaultBinarySolution
import org.uma.jmetal.solution.{BinarySolution, Solution}
import org.uma.jmetal.util.SolutionListUtils
import problem.attributes.{Clase, DiversityMeasure}
import problem.evaluator.EPMEvaluator
import problem.qualitymeasures
import problem.qualitymeasures._
import utils.{BitSet, Utils}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

object Filters {

  // enumeration for pattern matching on measure Filter
  sealed trait MeasureForThresholdFilter
    case object Accuracy extends MeasureForThresholdFilter
    case object AUC extends MeasureForThresholdFilter
    case object Confidence extends MeasureForThresholdFilter
    case object Coverage extends MeasureForThresholdFilter
    case object FPR extends MeasureForThresholdFilter
    case object GMean extends MeasureForThresholdFilter
    case object GrowthRate extends MeasureForThresholdFilter
    case object Jaccard extends MeasureForThresholdFilter
    case object OddsRatio extends MeasureForThresholdFilter
    case object SuppDiff extends MeasureForThresholdFilter
    case object Support extends MeasureForThresholdFilter
    case object TNR extends MeasureForThresholdFilter
    case object TPR extends MeasureForThresholdFilter
    case object WRAcc extends MeasureForThresholdFilter
    case object WRAccNorm extends MeasureForThresholdFilter

  /**
   * It remove the repeated patterns
   * @param patterns
   * @return
   */
  def repeatedFilter[S <: Solution[_]](patterns: Seq[S]): Seq[S] = {
    patterns.distinct
  }

  /**
   * It applies a token competition-based filter procedure for each class of the problem
   * @param patterns
   * @return
   */
  def tokenCompetitionFilter[S <: Solution[_]](evaluator: EPMEvaluator)(patterns: Seq[S]): Seq[S] = {

    val result: ArrayBuffer[S] = new ArrayBuffer[S]()
    val classes: Seq[Int] = patterns.map(getPatternClass).distinct
    val diversityMeasure: QualityMeasure = Utils.getQualityMeasure("WRAccNorm")

    for(clazz <- classes){
      val patternsOfClass = patterns.filter(ind => ind.getAttribute(classOf[Clase[S]]).asInstanceOf[Int] == clazz)
        .sortWith(sortPatternsByMeasure(diversityMeasure))

      var tokens = new BitSet(patternsOfClass.head.getAttribute(classOf[Coverage[S]]).asInstanceOf[BitSet].capacity)
      var counter = 0
      var allCovered = false

      do{

        val coverage = patternsOfClass(counter).getAttribute(classOf[Coverage[S]]).asInstanceOf[BitSet]

        // covered examples that belongs to the class
        val correct = coverage & evaluator.classes(clazz)  // TODO: Remove evaluator and introduce a Seq[BitSet] that marks the instances of the class

        val newCov = (tokens ^ correct) & (~tokens)
        if(newCov.cardinality() > 0){
          result += patternsOfClass(counter)
          tokens = tokens | correct
          //result.add(orderPop(counter))
        }

        // As we are covering only tokens of the class. All covered must be set when all examples of the given class are also covered
        if(tokens.cardinality() == tokens.capacity || (tokens & evaluator.classes(clazz)).cardinality() == evaluator.classes(clazz).cardinality() ){
          allCovered = true
        }
        counter += 1

      } while(counter < patternsOfClass.size && !allCovered)
    }

    result
  }

  /**
   * It keeps those patterns that are above the given threshold on the selected quality measure
   *
   * @note: IT IS BASED ON MEASURE MAXIMISATION
   * @param measure
   * @param threshold
   * @param patterns
   * @return
   */
  def measureThresholdFilter[S <: Solution[_]](measure: MeasureForThresholdFilter, threshold: Double)(patterns: Seq[S]): Seq[S]= {

   val qm: QualityMeasure = measure match {
     case Accuracy => new qualitymeasures.Accuracy
     case AUC => new qualitymeasures.AUC
     case Confidence => new qualitymeasures.Confidence
     case Coverage => new qualitymeasures.Coverage
     case FPR => new FPR
     case GMean => new GMean
     case GrowthRate => new GrowthRate
     case Jaccard => new Jaccard
     case OddsRatio => new OddsRatio
     case SuppDiff => new SuppDiff
     case Support => new Support
     case TNR => new TNR
     case TPR => new TPR
     case WRAcc =>new WRAcc
     case WRAccNorm => new WRAccNorm
   }


    val result: ArrayBuffer[S] = new ArrayBuffer[S]()
    val classes: Seq[Int] = patterns.map(getPatternClass).distinct

    for(i <- classes){
      val popClass = patterns.filter(getPatternClass(_) == i)
      val popFiltered = popClass.filter(ind => qm.calculateValue(ind.getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]) >= threshold)

      if(popFiltered.isEmpty){
        result += popClass.sortWith(sortPatternsByMeasure(qm)).head
      } else {
        result ++= popFiltered
      }
    }

    result
  }


  /**
   * It performs the coverage ratio filter proposed in:
   *
   * Li, J., Liu, J., Toivonen, H., Satou, K., Sun, Y., Sun, B.: Discovering statistically
   * non-redundant subgroups. Knowl.-Based Syst. 67, 315–327 (2014)
   *
   * First, it checks if two patterns covers have at least a given % of covered instances in common.
   * Next, it determines if the OddsRatioIntervals of both patterns overlaps. If they do, they are redundant. The pattern
   * with more variables is removed.
   *
   * @param maxCoveragePercent The % of instances in common
   * @tparam S An individual
   * @author Ángel Miguel García Vico (agvico@ujaen.es)
   */
  def coverageRatioFilter[S <: Solution[_]](evaluator: EPMEvaluator, maxCoveragePercent: Double)(patterns: Seq[S]): Seq[S] = {

   if(patterns.size < 2) {
      patterns
    } else {
      val classes: Seq[Int] = patterns.map(getPatternClass).distinct
      val result: ArrayBuffer[S] = new ArrayBuffer[S]()

      for(cl <- classes){
        val patternsClass = patterns.filter(getPatternClass(_) == cl)  // Get patterns of the current class

        if(patternsClass.size < 2) {
          result ++= patternsClass
        } else {
          val toRemove: BitSet = new BitSet(patternsClass.size)
          for(i <- patternsClass.indices){
            // pairwise comparison of patterns. If a patterns do not cover examples, directly remove it
            if(patternsClass(i).getAttribute(classOf[Coverage[BinarySolution]]).asInstanceOf[BitSet].cardinality() <= 0){
              toRemove.set(i)
            } else {
              for(j <- patternsClass.indices.drop(i+1)){
                //If a patterns do not cover examples, directly remove it
                if(patternsClass(j).getAttribute(classOf[Coverage[BinarySolution]]).asInstanceOf[BitSet].cardinality() <= 0){
                  toRemove.set(j)
                } else {
                  // Patterns i and j cover examples, do the coverage ratio comparison
                  val P1: BitSet = patternsClass(i).getAttribute(classOf[Coverage[BinarySolution]]).asInstanceOf[BitSet]
                  val P2: BitSet = patternsClass(j).getAttribute(classOf[Coverage[BinarySolution]]).asInstanceOf[BitSet]

                  val P1_pos: BitSet = P1 & evaluator.classes(cl)
                  val P2_pos: BitSet = P2 & evaluator.classes(cl)

                  val intersection: Double = (P1_pos & P2_pos).cardinality()
                  val sizeP1: Double = P1_pos.cardinality()
                  val sizeP2: Double = P2_pos.cardinality()

                  val overlapPct = Math.max(intersection / sizeP1, intersection / sizeP2)

                  if (overlapPct >= maxCoveragePercent) { // If one subsume another, keep the one with the best odds ratio
                    val contingencyTableP1 = patternsClass(i).getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]
                    val contingencyTableP2 = patternsClass(j).getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]

                    val oddsIntervalP1 = new OddsRatioInterval()
                    oddsIntervalP1.calculate(contingencyTableP1)

                    val oddsIntervalP2 = new OddsRatioInterval()
                    oddsIntervalP2.calculate(contingencyTableP2)

                    if(oddsIntervalP1.min > oddsIntervalP2.max){
                      // P2 is worse, remove it
                      toRemove.set(j)
                    } else if(oddsIntervalP2.min > oddsIntervalP1.max){
                      // P1 is worse, remove it
                      toRemove.set(i)
                    } else  if (oddsIntervalP1.overlap(oddsIntervalP2)) {
                      // if the overlap, we the difference between them are not significant, remove the one with the highest number of variables
                      if (getNumVars(patternsClass(i)) <= getNumVars(patternsClass(j))) {
                        toRemove.set(j)
                      } else {
                        toRemove.set(i)
                      }
                    }
                  }
                }
              }
            }
          }

          // After processing all the patterns of the class, save those we wanna keep
          for (i <- patternsClass.indices) {
            if (!toRemove.get(i)) {
              result += patternsClass(i)
            }
          }
        }
      }

      result
    }
  }


  /**
   * It returns the patterns that are non-dominated.
   *
   * Note that the patterns must be evaluated in the origin devices.
   *
   * @param patterns
   * @return
   */
  def nonDominatedFilter[S <: Solution[_]](patterns: Seq[S]): Seq[S] = {

   val result: ArrayBuffer[S] = new ArrayBuffer[S]()
    val classes: Seq[Int] = patterns.map(getPatternClass).distinct
    for(i <- classes){
      result ++= SolutionListUtils.getNondominatedSolutions(patterns.filter(getPatternClass(_) == i).asJava).asScala
    }
    result
  }


  /**
   * The identity filter. Return the same set of patterns as the input
   * @param patterns
   * @tparam S
   * @return
   */
  def identity[S <: Solution[_]](patterns: Seq[S]): Seq[S] = patterns


  /*****************************************
   *  PRIVATE METHODS AFTER THIS POINT
   *
   ******************************************/
  private def getPatternClass[S <: Solution[_]](s: S): Int = {
  s.getAttribute(classOf[Clase[S]]).asInstanceOf[Int]
}



  /**
   * Sorts the individuals according to the value of the diversity measure.
   * In case of tie, it selects the one with less active elements.
   *
   * @param x
   * @param y
   * @return
   */
  private def sortPatternsByDiversity[S <: Solution[_]](x: S, y: S): Boolean ={

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


  /**
   * Sorts the individuals according to the value of the given quality measure.
   *
   * It calculates the value of the sorting measure of each individual by means of the ContingencyTable Attribute
   *
   * @param measure
   * @param x
   * @param y
   * @tparam S
   * @return
   */
  private def sortPatternsByMeasure[S <: Solution[_]](measure: QualityMeasure)(x: S, y: S): Boolean = {
    val xtab: ContingencyTable = x.getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]
    val ytab: ContingencyTable = y.getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]
    val measureX: Double = measure.calculateValue(xtab)
    val measureY: Double = measure.calculateValue(ytab)
    measureX > measureY
  }


  /**
   * Get the numbers of active variables in the solutions
   * @param sol
   * @return
   */
  def getNumVars[S <: Solution[_]](sol: S): Int = {
    var vars: Int = 0

    sol match {

      case s : DefaultBinarySolution => {
        for (i <- 0 until sol.getNumberOfVariables) {
          if (s.getVariableValue(i).cardinality > 0 && s.getVariableValue(i).cardinality < s.getNumberOfBits(i))
            vars += s.getVariableValue(i).cardinality
        }
        vars
      }

      case _ => throw new UnsupportedOperationException("getNumVars with unknown type class")
    }

  }

}
