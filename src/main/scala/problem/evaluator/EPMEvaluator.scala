package problem.evaluator

import attributes.{Coverage, TestMeasures}
import org.apache.spark.rdd.RDD
import org.apache.spark.util.SizeEstimator
import org.uma.jmetal.problem.Problem
import org.uma.jmetal.solution.{BinarySolution, Solution}
import org.uma.jmetal.util.JMetalLogger
import org.uma.jmetal.util.solutionattribute.impl.DominanceRanking
import problem.attributes.{Attribute, Clase, DiversityMeasure}
import problem.qualitymeasures.exceptions.InvalidRangeInMeasureException
import problem.qualitymeasures.{ContingencyTable, QualityMeasure, WRAccNorm}
import problem.{EPMProblem, EPMSparkStreamingProblem}
import utils.BitSet

import java.util
import scala.collection.mutable.ArrayBuffer

/**
  * Class for the use of an improved DNF problem.evaluator with (or without) MapReduce
  */
class EPMEvaluator extends Evaluator[BinarySolution] {

  /**
    * The bitSets for the variables. They are distributed across the cluster.
    * Each row in the RDD represent a variable with different BitSets for each possible value
    */
  var bitSets: RDD[Seq[Seq[(Int, Int, BitSet)]]] = null


  /**
    * The bitsets for the variables in a non-distributed environment
    */
  var sets: Seq[Seq[BitSet]] = IndexedSeq.empty

  /**
    * The bitsets for the determination of the belonging of the examples for each class
    */
  var classes: Seq[BitSet] = IndexedSeq.empty

  /**
   * This bit set allows to mark instances are removed or forbidden, therefore, the do not count for the evaluation
   */
  var forbiddenInstances: BitSet = BitSet.empty

  /**
    * It determines if the evaluation must be performed using the RDD (distributed) or the Seq (local)
    */
  var bigDataProcessing = true


  /**
    * It initialises the bitset structure employed for the improved evaluation.
    *
    * @param problem the problem
    */
  override def initialise(problem: Problem[BinarySolution]): Unit = {
    if (problem.isInstanceOf[EPMProblem]) {
      //JMetalLogger.logger.info("Initialising the precalculated structure...")

      val problema = problem.asInstanceOf[EPMProblem]
      val attrs = problema.getAttributes
      val length = problema.getNumExamples
      JMetalLogger.logger.fine("Number of Examples: " + length)
      val t_ini = System.currentTimeMillis()


      if (this.bigDataProcessing) {
        bitSets = initaliseBigData(problema, attrs)

        // Calculate the bitsets for the classes
        val numclasses = attrs.last.numValues

        classes = getClassesBitSets(problem, numclasses, attrs, length)


      } else {
        sets = initialiseNonBigData(problema, attrs)
        val aux = new ArrayBuffer[BitSet]()
        for (i <- 0 until problema.getData.numClasses()) {
          aux += new BitSet(problema.getData.numInstances())
        }
        classes = aux

        val enum = problema.getData.enumerateInstances()
        var count = 0
        while (enum.hasMoreElements) { // for each instance
          val instance = enum.nextElement()
          classes(instance.classValue().toInt).set(count)
          count += 1
        }
      }


      super.setProblem(problema)
      if (bigDataProcessing) {
        JMetalLogger.logger.fine("Pre-calculation time: " + (System.currentTimeMillis() - t_ini) + " ms. Size of Structure: " + (SizeEstimator.estimate(bitSets) / Math.pow(1000, 2)) + " MB.")
      } else {
        JMetalLogger.logger.fine("Pre-calculation time: " + (System.currentTimeMillis() - t_ini) + " ms. Size of Structure: " + (SizeEstimator.estimate(sets) / Math.pow(1000, 2)) + " MB.")

      }


    }
  }


  /**
    * It gets the bit sets that represents the classes of the instances
    *
    * @param problem
    * @param numClasses
    * @param attrs
    * @param numExamples
    * @return
    */
  def getClassesBitSets(problem: Problem[BinarySolution], numClasses: Int, attrs: Seq[Attribute], numExamples: Int): Seq[BitSet] = {
    if (problem.isInstanceOf[EPMSparkStreamingProblem]) {
      val problema = problem.asInstanceOf[EPMSparkStreamingProblem]

      problema.getRDD().mapPartitions(partition => {
        val clase = new ArrayBuffer[BitSet]()
        for (i <- 0 until numClasses) {
          clase += new BitSet(numExamples)
        }
        partition.foreach(instance => {
          val index = instance._2.toInt
          val clazz = instance._1.last.toInt
          clase(clazz).set(index)
        })
        Array(clase).iterator
      }).treeReduce((x, y) => {
        for (i <- x.indices) {
          x(i) = x(i) | y(i)
        }
        x
      })

    } else {

      val problema = problem.asInstanceOf[EPMProblem]
      val datasetClasses = problema.getDataset.select("index", attrs.last.getName)


      datasetClasses.rdd.mapPartitions(x => {
        val clase = new ArrayBuffer[BitSet]()
        for (i <- 0 until numClasses) {
          clase += new BitSet(numExamples)
        }
        x.foreach(y => {
          val index = y.getLong(0).toInt
          val name = y.getString(1)
          clase(attrs.last.nominalValue.indexOf(name)).set(index)
        })
        val aux = new Array[ArrayBuffer[BitSet]](1)
        aux(0) = clase
        aux.iterator
      }).treeReduce((x, y) => {
        for (i <- x.indices) {
          x(i) = x(i) | y(i)
        }
        x
      })
    }
  }

// TODO: We need to check this in depth (there is a source of errors)
  override def evaluate(solutionList: util.List[BinarySolution], problem: Problem[BinarySolution]): util.List[BinarySolution] = {
    // In the map phase, it is returned an array of bitsets for each variable of the problem for all the individuals
    val t_ini = System.currentTimeMillis()


    val coverages = if (bigDataProcessing) {
      calculateBigData(solutionList, problem)
    } else {
      calculateCoveragesNonBigData(solutionList, problem)
    }

    //println("Size of coverages: " + SizeEstimator.estimate(coverages).toDouble / (1024.0 * 1024.0 * 1024.0) + " GB.")

    // Calculate the contingency table for each individual
    for (i <- coverages.indices) {
      val ind = solutionList.get(i)
      val cove = new Coverage[BinarySolution]()
      val diversity = new DiversityMeasure[BinarySolution]()

      if (!isEmpty(ind)) {
        cove.setAttribute(ind, coverages(i) - forbiddenInstances)
        val clase = ind.getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int]

        // tp = covered AND belong to the class   (Note that, in all cases, we remove the forbidden instances)
        val tp = (coverages(i) & classes(clase)) - forbiddenInstances

        // tn = NOT covered AND DO NOT belong to the class
        val tn = (~coverages(i)) & (~classes(clase)) - forbiddenInstances

        // fp = covered AND DO NOT belong to the class
        val fp = (coverages(i) & (~classes(clase))) - forbiddenInstances

        // fn = NOT covered AND belong to the class
        val fn =((~coverages(i)) & classes(clase)) - forbiddenInstances

        val table = new ContingencyTable(tp.cardinality(), fp.cardinality(), tn.cardinality(), fn.cardinality())
        val div = new WRAccNorm()
        div.calculateValue(table)
        diversity.setAttribute(ind, div)
        table.setAttribute(ind, table)

        val objectives = super.calculateMeasures(table)
        for (j <- objectives.indices) {
          if (objectives(j).isDefined) {
            ind.setObjective(j, objectives(j).get.getValue)
          }
        }
      } else {
        // If the rule is empty, set the fitness at minimum possible value for all the objectives.
        for (j <- 0 until ind.getNumberOfObjectives) {
          ind.setObjective(j, QualityMeasure.minimumValue())
        }
        val div = new WRAccNorm()
        val rank = new DominanceRanking[BinarySolution]()
        val table = new ContingencyTable(0,0,0,0)
        table.setAttribute(ind, table)
        div.setValue(QualityMeasure.minimumValue())
        rank.setAttribute(ind, Integer.MAX_VALUE)
        cove.setAttribute(ind, BitSet.ofSize(coverages.head.capacity))
        diversity.setAttribute(ind, div)
      }
    }

    // Once we've got the coverage of the rules, we can calculate the contingecy tables.
    return solutionList
  }


  /**
    * It evaluates against the test data.
    *
    * @param solutionList
    * @param problem
    * @return
    */
  def evaluateTest(solutionList: util.List[BinarySolution], problem: Problem[BinarySolution]): util.List[BinarySolution] = {
    // In the map phase, it is returned an array of bitsets for each variable of the problem for all the individuals
    val t_ini = System.currentTimeMillis()

    val coverages = if (bigDataProcessing) {
      calculateBigData(solutionList, problem)
    } else {
      calculateCoveragesNonBigData(solutionList, problem)
    }

    val table = new Array[ContingencyTable](coverages.length)
    for (i <- coverages.indices) {
      val ind = solutionList.get(i)
      val cove = new Coverage[BinarySolution]()
      val diversity = new DiversityMeasure[BinarySolution]()

      if (!isEmpty(ind)) {
        cove.setAttribute(ind, coverages(i))
        val clase = ind.getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int]

        // tp = covered AND belong to the class
        val tp = coverages(i) & classes(clase)

        // tn = NOT covered AND DO NOT belong to the class
        val tn = (~coverages(i)) & (~classes(clase))

        // fp = covered AND DO NOT belong to the class
        val fp = coverages(i) & (~classes(clase))

        // fn = NOT covered AND belong to the class
        val fn = (~coverages(i)) & classes(clase)

        table(i) = new ContingencyTable(tp.cardinality(), fp.cardinality(), tn.cardinality(), fn.cardinality(), coverages(i))
      } else {
        table(i) = new ContingencyTable(0, 0, 0, 0)
        cove.setAttribute(ind, new BitSet(coverages(i).capacity))
      }
    }


    for (i <- table.indices) {
      val ind = solutionList.get(i)
      val cove = new Coverage[BinarySolution]()
      val div = new WRAccNorm()
      div.calculateValue(table(i))
      val diversity = new DiversityMeasure[BinarySolution]()

      if (!isEmpty(ind)) {
        val measures = utils.ClassLoader.getClasses

        measures.foreach(q => {
          try {
            q.calculateValue(table(i))
            q.validate()

          } catch {
            case ex: InvalidRangeInMeasureException =>
              System.err.println("Error while evaluating Individuals in test: ")
              ex.showAndExit(this)
          }
        })

        val test = new TestMeasures[BinarySolution]()
        test.setAttribute(ind, measures)
        table(i).setAttribute(ind, table(i))
        diversity.setAttribute(ind, div)

      } else {
        // If the rule is empty, set the fitness at minimum possible value for all the objectives.
        for (j <- 0 until ind.getNumberOfObjectives) {
          ind.setObjective(j, QualityMeasure.minimumValue())
        }
        val div = new WRAccNorm()
        //val rank = new DominanceRanking[BinarySolution]()
        div.setValue(QualityMeasure.minimumValue())
        //rank.setAttribute(ind, Integer.MAX_VALUE)
        //cove.setAttribute(ind, new BitSet(coverages(i).capacity))
        diversity.setAttribute(ind, div)
      }
    }

    // Once we've got the coverage of the rules, we can calculate the contingecy tables.
    return solutionList
  }

  override def shutdown(): Unit = ???

  /**
    * It return whether the individual represents the empty pattern or not.
    *
    * @param individual
    * @return
    */
  override def isEmpty(individual: Solution[BinarySolution]): Boolean = {
    for (i <- 0 until individual.getNumberOfVariables) {
      if (participates(individual, i)) {
        return false
      }
    }
    return true

  }

  def isEmpty(individual: BinarySolution): Boolean = {
    for (i <- 0 until individual.getNumberOfVariables) {
      if (participates(individual, i)) {
        return false
      }
    }
    return true

  }

  /**
    * It returns whether a given variable of the individual participates in the pattern or not.
    *
    * @param individual
    * @param var
    * @return
    */
  override def participates(individual: Solution[BinarySolution], `var`: Int): Boolean = {
    val ind = individual.asInstanceOf[BinarySolution]

    if (ind.getVariableValue(`var`).cardinality() > 0 && ind.getVariableValue(`var`).cardinality() < ind.getNumberOfBits(`var`)) {
      true
    } else {
      false
    }
  }

  def participates(individual: BinarySolution, `var`: Int): Boolean = {

    if (individual.getVariableValue(`var`).cardinality() > 0 && individual.getVariableValue(`var`).cardinality() < individual.getNumberOfBits(`var`)) {
      true
    } else {
      false
    }
  }


  /**
    * It calculates the coverages bit sets for each individual in the population by means of performing the operations in
    * a local structure stored in {@code sets}.
    *
    * This function is much faster than Big Data one when the number of variables is LOW.
    *
    * @param solutionList
    * @param problem
    * @return
    */
  def calculateCoveragesNonBigData(solutionList: util.List[BinarySolution], problem: Problem[BinarySolution]): Seq[BitSet] = {
    val coverages = new Array[BitSet](solutionList.size())
    for (i <- coverages.indices) coverages(i) = new BitSet(sets.head.head.capacity)

    for (i <- 0 until solutionList.size()) {
      // for each individual
      val ind = solutionList.get(i)

      if (!isEmpty(ind)) {
        var first = true
        for (j <- 0 until ind.getNumberOfVariables) {
          // for each variable
          if (participates(ind, j)) {
            // Perform OR operations between active elements in the DNF rule.
            var coverageForVariable = new BitSet(sets.head.head.capacity)
            for (k <- 0 until ind.getNumberOfBits(j)) {
              if (ind.getVariableValue(j).get(k)) {
                coverageForVariable = coverageForVariable | sets(j)(k)
              }
            }
            // after that, perform the AND operation
            if (first) {
              coverages(i) = coverageForVariable | coverages(i)
              first = false
            } else {
              coverages(i) = coverages(i) & coverageForVariable
            }
          }
        }
      } else {
        coverages(i) = new BitSet(sets.head.head.capacity)
      }
    }

    coverages
  }


  /**
    * It calculates the coverages bit sets for each individual in the population by means of performing the operations in
    * a distributed RDD structure stored in {@code bitSets}.
    *
    * This function is much faster than local one when the number of variables is VERY HIGH.
    *
    * @param solutionList
    * @param problem
    * @return
    */
  def calculateBigData(solutionList: util.List[BinarySolution], problem: Problem[BinarySolution]): Seq[BitSet] = {

    val numExamples = problem.asInstanceOf[EPMProblem].getNumExamples

    bitSets.mapPartitions(x => {
      val bits = x.map(y => {
        //val index = y._2.toInt
        //val sets = y._1
        val min = y(0)(0)._1
        val max = y(0)(0)._2

        val coverages = new Array[BitSet](solutionList.size())
        for (i <- coverages.indices) {
          coverages(i) = new BitSet(max - min)
        }

        for (i <- 0 until solutionList.size()) {
          // for each individual
          val ind = solutionList.get(i)
          val clase = ind.getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int]
          if (!isEmpty(ind)) {
            var first = true
            for (j <- 0 until ind.getNumberOfVariables) {
              // for each variable
              if (participates(ind, j)) {
                // Perform OR operations between active elements in the DNF rule.
                var coverageForVariable = new BitSet(max - min)
                for (k <- 0 until ind.getNumberOfBits(j)) {
                  if (ind.getVariableValue(j).get(k)) {
                    coverageForVariable = coverageForVariable | y(j)(k)._3
                  }
                }
                // after that, perform the AND operation
                if (first) {
                  coverages(i) = coverageForVariable | coverages(i)
                  first = false
                } else {
                  coverages(i) = coverages(i) & coverageForVariable
                }
              }
            }
          } else {
            coverages(i) = new BitSet(y(0)(0)._3.capacity)
          }

          // Add to the bitSet zeros before min and max in order to have a full-length BitSet.
          // This allows us to perform OR operations on the reduce for the final coverage of the individual
          if (min > 0)
            coverages(i) = new BitSet(min).concatenate(min, coverages(i), max - min).get(0, max + 1)
        }
        coverages

      })
      bits
    }).treeReduce((x, y) => {
      for (i <- x.indices) {
        x(i) = x(i) | y(i)
      }
      x
    }, Evaluator.TREE_REDUCE_DEPTH)

  }


  def setBigDataProcessing(processing: Boolean): Unit = {
    bigDataProcessing = processing
  }


  /**
    * It returns the maximum belonging degree of the LLs defined for a variable
    *
    * @param problem
    * @param variable
    * @param x
    */
  def getMaxBelongingDegree(problem: Problem[BinarySolution], variable: Int, x: Double): Double = {
    val problema = problem.asInstanceOf[EPMProblem]
    val tope = 10E-12

    val max = problema.getFuzzyVariable(variable).map(memb => {
      memb.membership(x)
    }).max

    max - tope
  }


  /**
    * It initialises the BitSets structure for the {@code sets}. It store the coverage of each possible feature (var-value pair)
    * in a local structure, i.e., in the driver.
    *
    * @param problema
    * @param attrs
    * @return
    */
  def initialiseNonBigData(problema: EPMProblem, attrs: Seq[Attribute]): Seq[Seq[BitSet]] = {

    val enum = problema.getData.enumerateInstances()

    // Create for each selector (attribute-value pair) its corresponding bitset
    // This bit set is empty at the beginning
    val set: Seq[Seq[BitSet]] = problema.getAttributes.map(att => {
      val a = new ArrayBuffer[BitSet]()
      for (i <- 0 until att.numValues) {
        a += new BitSet(problema.getData.numInstances())
      }
      a
    })

    // for each instance calculate its belonging to each selector
    var instanceIndex: Int = 0

    while (enum.hasMoreElements) {
      val instance = enum.nextElement()

      for (variable <- problema.getAttributes.indices.dropRight(1)) {
        val attrValue: Double = instance.value(variable)
        if(attrValue.isNaN){
          // if NaN, the instance value is lost. Mark as covered by all the values of the variable by default.
          set(variable).foreach(_.set(instanceIndex))
        } else {
          if (problema.getAttributes(variable).isNominal) {
            // if nominal variable:
            // Set the value that correspond to the nominal value directly
            set(variable)(attrValue.toInt).set(instanceIndex)
          } else {
            // if numeric variable:
            // calculate the fuzzy value with the maximum membership degree
            val degrees: Seq[Double] = problema.getFuzzyVariable(variable).map(_.membership(attrValue))
            val max: Double = degrees.max
            val indexOfMax: Int = degrees.indexOf(max)
            set(variable)(indexOfMax).set(instanceIndex)
          }
        }
      }

      instanceIndex += 1
    }

    //return
    set

  }


  /**
    * It initialises the bitSets structure for the processing of the individuals in an RDD for distributed computing.
    *
    * @param problema
    * @param attrs
    * @return
    */
  def initaliseBigData(problema: EPMProblem, attrs: Seq[Attribute]): RDD[Seq[Seq[(Int, Int, BitSet)]]] = {

    if (problema.isInstanceOf[EPMSparkStreamingProblem]) {
      val ssProblem = problema.asInstanceOf[EPMSparkStreamingProblem]

      initialiseUsingSparkStreaming(ssProblem, ssProblem.getRDD(), attrs)
        .cache()

    } else {
      initialiseUsingDataFrame(problema, attrs)
        .cache()
    }

  }


  /**
    * It initialises the bit sets by means of a Spark DataFrame
    *
    * @param problema
    * @param attrs
    * @return
    */
  def initialiseUsingDataFrame(problema: EPMProblem, attrs: Seq[Attribute]): RDD[Seq[Seq[(Int, Int, BitSet)]]] = {

    problema.getDataset.rdd.mapPartitions(x => {
      var min = Int.MaxValue
      var max = -1

      // Instatiate the whole bitsets structure for each partition with length equal to the length of data
      val partialSet: Seq[Seq[(Int, Int, BitSet)]] = (0 until attrs.length - 1).map(i => {
        (0 until attrs(i).numValues).map(j =>
          (min, max, new BitSet(0))
        )
      })

      var counter = 0
      x.foreach(y => {
        val index = y.getLong(0).toInt
        if (index < min) min = index
        if (index > max) max = index

        for (i <- attrs.indices.dropRight(1)) {
          val ind = i + 1
          // For each attribute
          for (j <- 0 until attrs(i).numValues) {
            // for each label/value
            if (attrs(i).isNumeric) {
              // Numeric variable, fuzzy computation
              if (y.isNullAt(ind)) {
                partialSet(i)(j)._3.set(counter)
              } else {
                if (problema.membershipDegree(i, j, y.getDouble(ind)) >= getMaxBelongingDegree(problema, i, y.getDouble(ind))) {
                  partialSet(i)(j)._3.set(counter)
                } else {
                  partialSet(i)(j)._3.unset(counter)
                }
              }
            } else {
              // Discrete variable
              if (y.isNullAt(ind)) {
                partialSet(i)(j)._3.set(counter)
              } else {
                if (attrs(i).valueName(j).equals(y.getString(ind))) {
                  partialSet(i)(j)._3.set(counter)
                } else {
                  partialSet(i)(j)._3.unset(counter)
                }
              }
            }
          }
        }
        counter += 1
      })

      val set: Seq[Seq[(Int, Int, BitSet)]] = partialSet.map(i => {
        i.map(j => {
          (min, max, j._3)
        })
      })

      val aux = new Array[Seq[Seq[(Int, Int, BitSet)]]](1)
      aux(0) = set
      aux.iterator
    }, true)
  }

  /**
    * It initialise the bit sets structure by means of an RDD provided by Spark Streaming instead of a DataFrame
    *
    * @param problema
    * @param rdd
    * @param attrs
    * @return
    */
  def initialiseUsingSparkStreaming(problema: EPMSparkStreamingProblem, rdd: RDD[(Seq[Double], Long)], attrs: Seq[Attribute]): RDD[Seq[Seq[(Int, Int, BitSet)]]] = {
    rdd.mapPartitions(partition => {
      var min = Int.MaxValue
      var max = -1

      // Instatiate the whole bitsets structure for each partition with length equal to the length of data
      val partialSet: Seq[Seq[(Int, Int, BitSet)]] = (0 until attrs.length - 1).map(i => {
        (0 until attrs(i).numValues).map(j =>
          (min, max, new BitSet(0))
        )
      })
      var counter = 0
      partition.foreach(instance => {
        val index = instance._2.toInt
        val data = instance._1
        if (index < min) min = index
        if (index > max) max = index

        for (i <- attrs.indices.dropRight(1)) {
          // For each attribute (without class)
          val instanceValue = data(i)
          for (j <- 0 until attrs(i).numValues) {
            // for each label/value
            if (attrs(i).isNumeric) {
              // Numeric variable, fuzzy computation
              if (instanceValue.isNaN) {
                partialSet(i)(j)._3.set(counter)
              } else {
                if (problema.membershipDegree(i, j, instanceValue) >= getMaxBelongingDegree(problema, i, instanceValue)) {
                  partialSet(i)(j)._3.set(counter)
                } else {
                  partialSet(i)(j)._3.unset(counter)
                }
              }
            } else {
              // Discrete variable
              if (data(i).isNaN) {
                partialSet(i)(j)._3.set(counter)
              } else {
                if (attrs(i).valueName(j).equals(attrs(i).valueName(instanceValue.toInt))) {
                  partialSet(i)(j)._3.set(counter)
                } else {
                  partialSet(i)(j)._3.unset(counter)
                }
              }
            }
          }
        }
        counter += 1
      })

      val set: Seq[Seq[(Int, Int, BitSet)]] = partialSet.map(i => {
        i.map(j => {
          (min, max, j._3)
        })
      })

      val aux = new Array[Seq[Seq[(Int, Int, BitSet)]]](1)
      aux(0) = set
      aux.iterator
    })
  }

  def getPairsCoveringAnExample2(example: Int): Seq[(Int, Int)] = {
    for {
      i <- sets.indices
      j <- sets(i).indices
      if sets(i)(j).get(example)
    } yield(i,j)
  }

  /**
    * It gets the attribute-value pairs that covers a given example
    *
    * @param example The index of the example yo check
    * @return A Seq of pairs (Int, Int) which maeans that variable _1 with value _2 covers the given example
    */
  def getPairsCoveringAnExample(example: Int): Seq[(Int, Int)] = {
    // get the pairs that cover that example (no-bigdata)
    var pairs: ArrayBuffer[(Int, Int)] = new ArrayBuffer[(Int, Int)]()
    if (!bigDataProcessing) {
      for (i <- sets.indices) {
        // for each variable
        for (j <- sets(i).indices) {
          // for each value
          if (sets(i)(j).get(example)) {
            val add = (i, j)
            pairs += add
          }
        }
      }
    } else {
      pairs = bitSets.mapPartitions(x => {
        x.map(y => {
          val pairs: ArrayBuffer[(Int, Int)] = new ArrayBuffer[(Int, Int)]()
          for (i <- y.indices) {
            for (j <- y(i).indices) {
              val min = y(i)(j)._1
              val max = y(i)(j)._2

              if (example >= min && example <= max) {
                val ex = if (min != 0) example % min else example
                if (y(i)(j)._3.get(ex)) {
                  val add = (i, j)
                  pairs += add
                }
              }
            }
          }

          pairs
        })
      }).treeReduce(_ ++ _)

    }
    pairs
  }

  /**
   * It marks the given BitSet as forbidden instances.
   *
   * @param marks A bitset of size equal to the problem's size.
   * @return
   */
  def addForbiddenInstances(marks: BitSet): Unit = {
       forbiddenInstances |= marks

  }


}
