/*
 * The MIT License
 *
 * Copyright 2018 Ángel Miguel García Vico.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *//*
 * The MIT License
 *
 * Copyright 2018 Ángel Miguel García Vico.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package utils

import attributes.TestMeasures
import org.uma.jmetal.solution.BinarySolution
import problem.EPMProblem
import problem.attributes.{Clase, DiversityMeasure}
import problem.evaluator.EPMEvaluator
import problem.qualitymeasures.{ContingencyTable, GlobalSupport, QualityMeasure}

import java.io.{File, FileWriter}
import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale

/**
 * Class to store the results and rules of a given population of individuals
 *
 * @author Ángel Miguel García Vico (agvico@ujaen.es)
 * @since JDK 8
 * @version 1.0
 */
final class ResultWriter(/**
                          * The path where the objectives values for each individual is stored
                          */
                         val pathTra: String,

                         /**
                          * The path where the test quality measures are stored (detailed file)
                          */
                         val pathTst: String,

                         /**
                          * The path where the test quality measures are stored (summary file)
                          */
                         val pathTstSummary: String,

                         /**
                          * The path where the rules are stored
                          */
                         val pathRules: String,

                         /**
                          * The population to get the results
                          */
                         var population: Seq[BinarySolution],

                         /**
                          * The instance where the variables are obtained
                          */
                         var problem: EPMProblem,

                         /**
                          * The objectives used in the algorithm
                          */
                         var objectives: Seq[QualityMeasure], val overwrite: Boolean) {

  if (this.population != null)
    population.sortBy(x => x.getAttribute(classOf[Clase[_]]).asInstanceOf[Int])
  //this.population.sort(Comparator.comparingInt((x: BinarySolution) => x.getAttribute(classOf[Clase[_]]).asInstanceOf[Int]))

  if (overwrite) {
    val a = Array(new File(pathTra), new File(pathTst), new File(pathTstSummary), new File(pathRules))
    a.foreach(f => if (f.exists()) f.delete())
  }


  def setPopulation(population: Seq[BinarySolution]) = {
    population.sortBy(x => x.getAttribute(classOf[Clase[_]]).asInstanceOf[Int])
    this.population = population
  }


  /**
   * The symbols to use in the formatter
   */
  private val symbols: DecimalFormatSymbols = new DecimalFormatSymbols(Locale.GERMANY)
  symbols.setDecimalSeparator('.')
  symbols.setNaN("NaN")
  symbols.setInfinity("INFINITY")


  /**
   * The formatter of the numbers
   */
  private val sixDecimals: DecimalFormat = new DecimalFormat("0.000000", symbols)

  /**
   * It determines if it is the first time to write the header or not
   */
  private var firstTime = true

  /**
   * It only writes the results of the rules
   */
  def writeRules() = {
    var content = ""
    for (i <- population.indices) {
      content += "Rule " + i + "\n"
      content += toString(population(i)) + "\n"
    }
    Files.addToFile(pathRules, content)
  }

  /**
   * It only writes the results of the objectives
   */
  def writeTrainingMeasures() = {
    var content = ""
    // Write the header (the consequent first, and next, the objective quality measures, finaly, the diversity measure)
    content += "Rule\tID\tConsequent"
    objectives.foreach(q => content += "\t" + q.getShortName)
    content += "\n"
    val attrs = problem.getAttributes

    // Now, for each individual, writes the training measures
    var i = 0
    population.foreach(ind => {
      content += i + "\t" + ind.hashCode() + "\t" + attrs(attrs.length - 1).valueName(ind.getAttribute(classOf[Clase[_]]).asInstanceOf[Int]) + "\t"
      ind.getObjectives.foreach(d => {
        content += sixDecimals.format(d) + "\t"
      })
      content += "\n"
      i += 1
    })
    Files.addToFile(pathTra, content)
  }

  /**
   * It writes the full version of the results test quality measures, i.e.,
   * the whole set of measures for each individual on each timestamp, in
   * addition to the summary
   */
  def writeTestFullResults() = {
    // this array stores the sum of the quality measures for the average
    //val averages: Seq[Double] = population.head.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].map( q => 0.0)
    var numVars = 0.0

    // First, write the headers
    var content = "Rule\tClass\tID\tNumRules\tNumVars\tTP\tFP\tTN\tFN"

    // now, append each test quality measure
    population.head.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].foreach(q => {
      content += "\t" + q.getShortName
    })
    content += "\n"

    val attrs = problem.getAttributes
    // now write the test results for each individual
    var i: Int = 0
    population.foreach(ind => {
      val vars = getNumVars(ind)
      content += i + "\t" + attrs.last.valueName(ind.getAttribute(classOf[Clase[_]]).asInstanceOf[Int]) + "\t" + ind.hashCode() + "\t" + "------\t" + sixDecimals.format(vars) + "\t"
      numVars += vars
      val table = ind.getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]
      content += table.getTp + "\t"
      content += table.getFp + "\t"
      content += table.getTn + "\t"
      content += table.getFn + "\t"
      val objs = ind.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]]
      objs.foreach(q => { val measure: Double = if(QualityMeasure.areInversed()) q.getValueInversed else q.getValue
        content += sixDecimals.format(measure) + "\t"
      })
      content += "\n"
      i += 1
    })

    // Calculate the averages of the test quality measures
    val averages: Seq[Double] = population.map(ind => {
      ind.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].map(_.getValue)
    }).reduce((x, y) => {
      x.zip(y).map { case (x: Double, y: Double) => x + y }
    })

    numVars /= population.size.toDouble
    // finally, write the average results
    content += "------\t------\t------\t" + sixDecimals.format(population.size) + "\t" + sixDecimals.format(numVars) + "\t-----\t------\t------\t------\t"
    for (d <- averages) {
      content += sixDecimals.format(d / population.size.toDouble) + "\t"
    }
    content += "\n"
    Files.addToFile(pathTst, content)
  }

  /**
   * It writes the results of the individuals in the files
   */
  def writeResults(time_ms: Long) = {
    writeRules()
    writeTrainingMeasures()
    writeTestFullResults()
    firstTime = false
  }

  /**
   * It writes the results of the individuals in the files
   */
  def writeStreamingResults(timestamp: Int, execTime: Long, memory: Double) = {
    //writeStreamingRules(timestamp)
    writeStreamingTrainingMeasures(timestamp)
    writeStreamingTestFullResults(timestamp)
    writeStreamingSummaryTestResults(timestamp, execTime, memory)
    firstTime = false
  }

  /**
   * @return the population
   */
  def getPopulation = population

  /**
   * It returns the String representation of the given rule.
   *
   * @param sol
   * @return
   */
  def toString(sol: BinarySolution) = {
    var result = ""
    val attrs = problem.getAttributes
    for (i <- 0 until sol.getNumberOfVariables) {
      if (sol.getVariableValue(i).cardinality > 0 && sol.getVariableValue(i).cardinality < sol.getNumberOfBits(i)) {
        result += "\tVariable " + attrs(i).getName + " = "
        for (j <- 0 until sol.getNumberOfBits(i)) {
          if (sol.getVariableValue(i).get(j))
            if (attrs(i).isNominal)
              result += attrs(i).valueName(j) + "  "
            else
              result += "Label " + j + ": " + problem.getFuzzySet(i, j).toString + "  "
        }
        result += "\n"
      }
    }
    result += "Consequent: " + attrs.last.valueName(sol.getAttribute(classOf[Clase[_]]).asInstanceOf[Int]) + "\n\n"
    result
  }

  def getNumVars(sol: BinarySolution) = {
    var vars = 0
    for (i <- 0 until sol.getNumberOfVariables) {
      if (sol.getVariableValue(i).cardinality > 0 && sol.getVariableValue(i).cardinality < sol.getNumberOfBits(i))
        vars += 1
    }
    vars
  }


  /**
   * It writes the rules in an streaming fashion
   *
   * @param timestamp
   */
  def writeStreamingRules(timestamp: Int) = {
    var content = "*************************************************\n"
    content += "Timestamp " + timestamp + ":\n"

    content += population.zipWithIndex.map(x => {
      var str = "Rule: " + x._2 + "(ID: " + x._1.hashCode() + ")\n"
      str += x._1.toString() + "\n"
      str
    }).reduce(_ + "\n" + _)
    content += "\n"

    Files.addToFile(pathRules, content)
  }


  /**
   * It writes the streaming traning measures
   *
   * @param timestamp
   */
  def writeStreamingTrainingMeasures(timestamp: Int): Unit = {
    var content = ""
    if (firstTime) { // Write the header (the consequent first, and next, the objective quality measures, finaly, the diversity measure)
      content += "Timestamp\tRule\tID\tConsequent"

      for (q <- objectives) {
        content += "\t" + q.getShortName
      }
      content += "\t" + population.head.getAttribute(classOf[DiversityMeasure[BinarySolution]]).asInstanceOf[QualityMeasure].getShortName + "(Diversity)"
      content += "\n"
    }

    // Now, for each individual, writes the training measures
    for (i <- population.indices) {
      content += sixDecimals.format(timestamp) + "\t" + i + "\t" + population(i).hashCode() + "\t" + population(i).getAttribute(classOf[Clase[BinarySolution]]) + "\t"
      for (j <- population(i).getObjectives) {
        content += sixDecimals.format(j) + "\t"
      }
      content += sixDecimals.format(population(i).getAttribute(classOf[DiversityMeasure[BinarySolution]]).asInstanceOf[QualityMeasure].getValue) + "\n"
    }

    Files.addToFile(pathTra, content)
  }


  /**
   * It writes the streaming test full results
   *
   * @param timestamp
   */
  def writeStreamingTestFullResults(timestamp: Int): Unit = {
    // this array stores the sum of the quality measures for the average
    //val averages: Seq[Double] = population.head.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].map( q => 0.0)
    var numVars = 0.0

    var content = ""
    if (firstTime) {
      // First, write the headers
      content = "Timestamp\tRule\tClass\tID\tNumRules\tNumVars\tTP\tFP\tTN\tFN"

      // now, append each test quality measure
      population.head.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].foreach(q => {
        content += "\t" + q.getShortName
      })
      content += "\n"
    }

    val attrs = problem.getAttributes
    // now write the test results for each individual
    var i: Int = 0
    population.foreach(ind => {
      val vars = getNumVars(ind)
      content += sixDecimals.format(timestamp) + "\t" + sixDecimals.format(i) + "\t" + attrs.last.valueName(ind.getAttribute(classOf[Clase[_]]).asInstanceOf[Int]) + "\t" + ind.hashCode() + "\t" + "------\t" + sixDecimals.format(vars) + "\t"
      numVars += vars
      val table = ind.getAttribute(classOf[ContingencyTable]).asInstanceOf[ContingencyTable]
      content += table.getTp + "\t"
      content += table.getFp + "\t"
      content += table.getTn + "\t"
      content += table.getFn + "\t"
      val objs = ind.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]]
      objs.foreach(q => {
        if(QualityMeasure.areInversed())
          content += sixDecimals.format(q.getValueInversed)
        else
          content += sixDecimals.format(q.getValue)
        content += "\t"
      })
      content += "\n"
      i += 1
    })

    // Calculate the averages of the test quality measures
    val averages: Seq[Double] = population.map(ind => {
      ind.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].map( qm => {
        if(QualityMeasure.areInversed()) qm.getValueInversed else qm.getValue
      })
    }).reduce((x, y) => {
      x.zip(y).map { case (x: Double, y: Double) => x + y }
    })

    numVars = population.map(getNumVars(_)).sum / population.size.toDouble

    // finally, write the average results
    content += "------\t------\t------\t------\t" + sixDecimals.format(population.size) + "\t" + sixDecimals.format(numVars) + "\t-----\t------\t------\t------\t"
    for (d <- averages) {
      content += sixDecimals.format(d / population.size.toDouble) + "\t"
    }
    content += "\n"
    Files.addToFile(pathTst, content)
  }


  /**
   * It writes the streaming summary results
   *
   * @param timestamp
   * @param execTime
   * @param memory
   */
  def writeStreamingSummaryTestResults(timestamp: Int, execTime: Long, memory: Double): Unit = {
    // this array stores the sum of the quality measures for the average
    //val averages: Seq[Double] = population.head.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].map( q => 0.0)
    var numVars = 0.0

    var content = ""
    if (firstTime) {
      // First, write the headers
      content = "Timestamp\tNumRules\tNumVars\tTP\tFP\tTN\tFN"

      // now, append each test quality measure
      population.head.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].foreach(q => {
        content += "\t" + q.getShortName
      })
      content += "\tExec_time_ms\tMemory_mb"
      content += "\n"
    }

    // Calculate the averages of the test quality measures
    val averages: Seq[Double] = population.map(ind => {
      ind.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].map(qm => {
        if(QualityMeasure.areInversed())
          qm.getValueInversed
        else
          qm.getValue
      })
    }).reduce((x, y) => {
      x.zip(y).map { case (x: Double, y: Double) => x + y }
    })

    numVars = population.map(getNumVars).sum / population.size.toDouble

    // finally, write the average results
    content += sixDecimals.format(timestamp) + "\t" + sixDecimals.format(population.size) + "\t" + sixDecimals.format(numVars) + "\t-----\t------\t------\t------\t"
    for (d <- averages) {
      content += sixDecimals.format(d / population.size.toDouble) + "\t"
    }
    content += sixDecimals.format(execTime) + "\t" + sixDecimals.format(memory)
    content += "\n"
    Files.addToFile(pathTstSummary, content)
  }
}

  object ResultWriter {
    val symbols: DecimalFormatSymbols = new DecimalFormatSymbols(Locale.GERMANY)
    symbols.setDecimalSeparator('.')
    symbols.setNaN("NaN")
    symbols.setInfinity("INFINITY")
    val sixDecimals: DecimalFormat = new DecimalFormat("0.000000", symbols)
    var firstTimeStream = true
    var firstTimeRules = true

    def writeStreamingRules(file: String, population: Seq[BinarySolution], timestamp: Long): Unit = {
      if(firstTimeRules){
        val f = new File(file)
        if (f.exists()) f.delete()
        firstTimeRules = false
      }
      val content: String = "*************************************************\n" +
                            "Timestamp " + timestamp + ":\n" +
                            population.zipWithIndex.map( x => {
                              var str = "Rule: " + x._2 + "(ID: " + x._1.hashCode() + ")\n"
                              str += x._1.toString() +"\n"
                              str
                            }).reduce(_ + "\n" + _) +
                            "\n"
      writeContent(file, content)

    }


    def writeRules(file: String, population: Seq[BinarySolution]): Unit = {
      val f = new File(file)
      if (f.exists()) f.delete()

      val content: String = population.zipWithIndex
                            .foldLeft("")((s,ind) => s + "Rule " + ind._2 + "\n" + ind._1.toString + "\n")

      writeContent(file, content)
    }

    def writeTrainingMeasures(file: String, population: Seq[BinarySolution], objectives: Seq[QualityMeasure], problem: EPMProblem): Unit = {
      val f = new File(file)
      if (f.exists()) f.delete()

      val content: String = "Rule\tID\tConsequent" +
        objectives.foldLeft("")((s, q) => s + "\t" + q.getShortName) + "\n" +  // Header
        population.zipWithIndex     // For each individual
          .foldLeft("")((s, ind) =>
            s + ind._2 + "\t" + ind._1.hashCode() + "\t" + problem.getAttributes.last.valueName(ind._1.getAttribute(classOf[Clase[_]]).asInstanceOf[Int]) + // Rule, id and class
              "\t" + ind._1.getObjectives.foldLeft("")((x,y) => x + sixDecimals.format(y) + "\t") + "\n")  // Objectives

      writeContent(file, content)
    }




    /**
     * It writes the streaming summary results
     *
     * @param timestamp
     * @param execTime
     * @param memory
     */
    def writeStreamingSummaryTestResults(file: String, population: Seq[BinarySolution], evaluator: EPMEvaluator, timestamp: Int, execTime: Long, memory: Double): Unit = {
      // this array stores the sum of the quality measures for the average
      //val averages: Seq[Double] = population.head.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].map( q => 0.0)
      var numVars = 0.0

      var content = ""
      if (firstTimeStream) {
        val f = new File(file)
        if (f.exists()) f.delete()

        // First, write the headers
        content = "Timestamp\tNumRules\tNumVars\tTP\tFP\tTN\tFN"

        // now, append each test quality measure
        population.head.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].foreach(q => {
          content += "\t" + q.getShortName
        })

        // Append global support

        content += "\tGlobalSupport\tExec_time_ms\tMemory_mb"
        content += "\n"
        firstTimeStream = false
      }

      // Calculate the averages of the test quality measures
      val averages: Seq[Double] = population.map(ind => {
        ind.getAttribute(classOf[TestMeasures[_]]).asInstanceOf[Seq[QualityMeasure]].map(_.getValue)
      }).reduce((x, y) => {
        x.zip(y).map { case (x: Double, y: Double) => x + y }
      })

      numVars = population.map(getNumVars(_)).sum / population.size.toDouble

      // finally, write the average results
      content += sixDecimals.format(timestamp) + "\t" + sixDecimals.format(population.size) + "\t" + sixDecimals.format(numVars) + "\t-----\t------\t------\t------\t"
      for (d <- averages) {
        content += sixDecimals.format(d / population.size.toDouble) + "\t"
      }
      content += sixDecimals.format(GlobalSupport(population, evaluator.classes)) + "\t"
      content += sixDecimals.format(execTime) + "\t" + sixDecimals.format(memory)
      content += "\n"


      writeContent(file, content)
    }



    private def writeContent(file: String, content: String): Unit ={
      val f: FileWriter = new FileWriter(file, true)
      try{
        f.write(content)
      } finally {
        f.close()
      }
    }

  private def getNumVars(sol: BinarySolution) = {
    var vars = 0
    for (i <- 0 until sol.getNumberOfVariables) {
      if (sol.getVariableValue(i).cardinality > 0 && sol.getVariableValue(i).cardinality < sol.getNumberOfBits(i))
        vars += 1
    }
    vars
  }



  }




