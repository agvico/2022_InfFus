package genetic.individual

import org.uma.jmetal.solution.BinarySolution
import org.uma.jmetal.solution.impl.DefaultBinarySolution
import org.uma.jmetal.util.binarySet.BinarySet
import problem.EPMProblem
import problem.attributes.Clase

import java.util
import scala.collection.JavaConverters._

// TODO: TEST THIS!!!

/**
  * A class for representing Emerging pattern mining patterns with disjunctive normal form
  *
  * @param problem The emerging pattern mining problem associated to this solution
  */
class EPMBinarySolution(problem: EPMProblem) extends DefaultBinarySolution(problem){


  /**
    * Return the hash code associated to this class. The hash code depends on the active variables and the class value
    * if present
    *
    * @return an integer with the associated hashCode
    */
  override def hashCode(): Int = {
    val clase = attributes.get(classOf[Clase[BinarySolution]])

    val hash = getVariables.asScala.foldLeft(1)(
      (hash, variable) => {
        val varHash = if(!participates(variable)) 0 else variable.hashCode()
        31 * hash + varHash
      }
    )
    /*var hash = 1

    for(vars <- getVariables.asScala){
      val varHash = if(!participates(vars)) 0 else vars.hashCode()
      hash = 31 * hash + varHash
    }*/

    if(clase != null) {
     hash + clase.asInstanceOf[Int]
    } else {
      hash
    }

  }


  /**
    * It copies a given Pattern
    * @return a new pattern
    */
  override def copy(): EPMBinarySolution = {
     val solution = new EPMBinarySolution(this.problem)

    for( i <- 0 until getNumberOfVariables){
      solution.setVariableValue(i, this.getVariableValue(i).clone.asInstanceOf[BinarySet])
    }
    for( i <- 0 until getNumberOfObjectives){
      solution.setObjective(i, this.getObjective(i))
    }

    solution.attributes = new util.HashMap[AnyRef, AnyRef](this.attributes)
    solution
  }


  /**
    * Checks whether two solutions are equals. Two solutions are equals if their participant variables are equals.
    * @param o The object to compare to
    * @return true if equals, false otherwise
    */
  override def equals(o: Any): Boolean = {

    o match {
      case that: EPMBinarySolution => {
        if(this.getVariables.size() != that.getVariables.size()){
          // Number of variables does not match
          false
        } else if (this.isEmpty() && that.isEmpty()){
          // If both are empty, they are equal
          true
        } else if (!this.getAttribute(classOf[Clase[BinarySolution]]).equals(that.getAttribute(classOf[Clase[BinarySolution]]))){
          // classes do not match
          false
        } else {
          // Check if antecedents are equals (remember to check non-participant variables)
          val diffFound = this.getVariables
            .asScala
            .zip(that.getVariables.asScala)
            .view                               // lazy processing: stops computing when the first occurrence is found
            .find(x => differentVariables(x._1, x._2))

          diffFound match {
            case Some(value) => false  // Differences found, return false
            case None => true          // NO  differences found, return true
          }
        }
      }

      case _ => false  // if classes does not match, return false.
    }

    /*if(!this.getClass.equals(o.getClass)) return false
    val other = o.asInstanceOf[EPMBinarySolution]

    if(this.getVariables.size() != other.getVariables.size()) return false

    if(this.isEmpty() && other.isEmpty()) return true

    // Check if antecedents are equals (remember to check non-participant variables)
    for(i <- 0 until this.getVariables.size()){

      val thisVariable = this.getVariableValue(i)
      val otherVariable = other.getVariableValue(i)

      val thisParticipate = this.participates(i)
      val otherParticipate = other.participates(i)

      // one variable participates while the other one not. Return false
      if(thisParticipate != otherParticipate) return false

      if(thisParticipate && otherParticipate){
          // both variables participates, check equality
         if(! (thisVariable equals otherVariable) ) return false
      }
    }

    // Check class & return
    val thisClass = this.getAttribute(classOf[Clase[BinarySolution]])
    val otherClass = other.getAttribute(classOf[Clase[BinarySolution]])

    thisClass equals otherClass */
  }


  /**
    * It determines whether the given variable participates in the antecedent part of the rule
    * @param variable
    * @return
    */
  def participates(variable: Int): Boolean = {
    val cardinality = this.getVariableValue(variable).cardinality()
    val maxLength = this.getVariableValue(variable).getBinarySetLength

    cardinality > 0 && cardinality < maxLength
  }


  /**
    * It determines whether the given variable participates in the antecedent part of the rule
    * @param variable
    * @return
    */
  def participates(variable: BinarySet): Boolean = {
    variable.cardinality() > 0 && variable.cardinality() < variable.getBinarySetLength
  }

  /**
   * It checks whether two variables are different
   * @param v1
   * @param v2
   * @return
   */
  private def differentVariables(v1: BinarySet, v2: BinarySet): Boolean = {
    val p1 = participates(v1)
    val p2 = participates(v2)
    if(p1 && p2){  // both participates: its contents must be the same
      !(v1 equals v2)
    } else {    // If not, check whether one participates while the other does not
      p1 != p2
    }
  }

  /**
    * It converts the given pattern to a human-readable String
    * @return
    */
  override def toString: String = {

    if(isEmpty()) return "Empty Rule"

    val attrs = problem.getAttributes
    var att = 0

    val antecedentString = getVariables.asScala.map(x => {

      val value = if(participates(x)){
        var content: String = "\tVariable " + attrs(att).getName + " = "
        if(attrs(att).isNominal) {
          for (i <- 0 until x.getBinarySetLength) {
            if(x.get(i))
              content += attrs(att).valueName(i) + "\t"
          }
        } else {
          for (i <- 0 until x.getBinarySetLength) {
            if(x.get(i))
              content += "Label " + i + " (" + problem.getFuzzySet(att, i).toString + ")\t"
          }
        }
        content
      } else {
        null
      }
      att += 1
      value
    }).filter(x => x != null).reduce(_ + "\n" + _)

    val clase = getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int]

    antecedentString + "\nConsequent: " + attrs.last.valueName(clase)

  }


  /**
    * It checks whether the given pattern is an empty pattern or not
    * @return
    */
  def isEmpty(): Boolean = {
    for(i <- 0 until getNumberOfVariables){
      if(participates(i)) return false
    }
    true
  }

}
