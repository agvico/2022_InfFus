package utils

import attributes.TestMeasures
import moa.options.ClassOption
import moa.streams.generators._
import moa.streams.{ArffFileStream, ConceptDriftStream, InstanceStream}
import org.apache.commons.math3.distribution.{NormalDistribution, TDistribution}
import org.apache.spark.SparkConf
import org.uma.jmetal.solution.{BinarySolution, Solution}
import org.uma.jmetal.util.pseudorandom.JMetalRandom
import problem.attributes.Clase
import problem.qualitymeasures.{LinearCombination, QualityMeasure}


/**
  * miscellaneous useful functions
  */
object Utils {

  /**
    * Calculate the mean of a set of numbers
    *
    * @param list
    * @tparam T
    * @return
    */
  def mean[T: Numeric](list: Seq[T]): Double = {
    val suma: T = list.sum
    val sum: Double = implicitly[Numeric[T]].toDouble(suma)

    sum / list.size.toDouble
  }


  /**
    * Calcualtes the standard deviation of a set of parameters
    *
    * @param list
    * @tparam T
    * @return
    */
  def standardDeviation[T: Numeric](list: Seq[T]): Double = {
    val media = mean(list)
    math.sqrt(list.map(x => {
      val value = implicitly[Numeric[T]].toDouble(x)
      math.pow(value - media, 2)
    })
      .sum / (list.size - 1.0))
  }

  /**
    * It calculates the confidence interval for the mean with the given confidence level
    *
    * @param list
    * @param confidenceLevel
    * @tparam T
    * @return a pair with the bounds of the interval
    */
  def meanConfidenceInterval[T: Numeric](list: Seq[T], confidenceLevel: Double): (Double, Double) = {
    if (list.length == 1) {
      val avg = mean(list)
      return (avg - 0.1, avg + 0.1)
    }

    if (list.length >= 30) {
      zScoreConfidenceInterval(list, confidenceLevel)
    } else {
      tTestConfidenceInterval(list, confidenceLevel)
    }
  }

  /**
   * The softmax function
   * @param list
   * @tparam T
   * @return
   */
  def softMax[T: Double](list: Double*): Seq[Double] = {
    val den: Double = list.map(math.exp).sum
    list.map(math.exp(_) / den)
  }

  private def tTestConfidenceInterval[T: Numeric](list: Seq[T], confidence: Double): (Double, Double) = {
    val t = new TDistribution(list.length - 1)
    val avg: Double = mean(list)
    val n: Double = list.length
    val value: Double = standardDeviation(list) / math.sqrt(n)
    val statistic: Double = t.inverseCumulativeProbability(1 - (1 - confidence) / 2)
    val intervalWidth: Double = statistic * value

    (avg - intervalWidth, avg + intervalWidth)
  }


  private def zScoreConfidenceInterval[T: Numeric](list: Seq[T], confidence: Double): (Double, Double) = {
    val avg = mean(list)
    val std = standardDeviation(list)
    val n = if(std != 0) new NormalDistribution(avg, std)  else new NormalDistribution(avg, 0.000001)
    val z_alfa_2 = n.inverseCumulativeProbability(1 - (1 - confidence / 2))
    val intervalWidth = z_alfa_2 * (std / math.sqrt(list.length))

    (avg - intervalWidth, avg + intervalWidth)
  }



  /**
   * It returns a quality measure class by means of a string name.
   *
   * The quality measure must match the name of the class.
   *
   * The LinearCombination measure format is the following:
   *    LinearCombination:M1;M2;M3...:w1;w2;w3...
   *
   *   where M1 is a measure and w1 is the weight associated to w1. All w_i must sum 1. Example:
   *
   *   LinearCombination:WRAccNorm;SuppDiff;Confidence:0.4;0.4;0.2
   *
   * @param name
   * @return
   */
  def getQualityMeasure(name: String): QualityMeasure = {
    if(name.startsWith("LinearCombination:")){
      val strSplitted = name.split(":")
      val measures = strSplitted(1).split(";").map(getMeasure)
      val weights: Seq[Double] = if(strSplitted.length < 3)
        for(i <- measures.indices) yield 1.0 / measures.length
        else
        strSplitted(2).split(";").map(_.toDouble)
      new LinearCombination(measures, weights)
    } else {
      getMeasure(name)
    }
  }

  private def getMeasure(name: String): QualityMeasure = {
    Class.forName(
      classOf[QualityMeasure].getPackage.getName +
        "." +
        name)
      .getDeclaredConstructor()
      .newInstance()
      .asInstanceOf[QualityMeasure]
  }

  def createDataGenerator(generatorString: String): InstanceStream = {

    val a: InstanceStream = ClassOption.cliStringToObject(generatorString, classOf[InstanceStream], null).asInstanceOf[InstanceStream]

    a match {
      case l: SEAGenerator => {l.prepareForUse(); l}
      case l: RandomTreeGenerator => {l.prepareForUse(); l}
      case l: MixedGenerator => {l.prepareForUse(); l}
      case l: RandomRBFGenerator => {l.prepareForUse(); l}
      case l: AgrawalGenerator => {l.prepareForUse(); l}
      case l: ArffFileStream => {l.prepareForUse(); l}
      case l: HyperplaneGenerator => {l.prepareForUse(); l}
      case l: LEDGenerator => {l.prepareForUse(); l}
      case l: STAGGERGenerator => {l.prepareForUse(); l}
      case l: SineGenerator => {l.prepareForUse(); l}
      case l: WaveformGenerator => {l.prepareForUse(); l}
      case l: ConceptDriftStream => {l.prepareForUse(); l}
      case _ => a
    }

  }


  /**
   * It returns a test quality measure from an individual.
   *
   * @param ind           The individual
   * @param measure       The short name of the measure
   * @tparam S            The individual type (subclass of Solution)
   * @return              Some() is measure is found, None otherwise.
   */
  def getMeasureInChromosome[S <: Solution[_]](ind: S, measure: String): Option[QualityMeasure] = {
    ind.getAttribute(classOf[TestMeasures[S]])
      .asInstanceOf[Seq[QualityMeasure]]
      .find(_.getShort_name equals( measure))
  }

  /**
   * It returns the spark configuration
   * @return
   */
  def getSparkConfiguration: SparkConf = {
    val conf = new SparkConf()
    conf.registerKryoClasses(
      Array(
        classOf[scala.collection.mutable.WrappedArray.ofRef[_]],
        classOf[org.apache.spark.sql.types.StructType],
        classOf[Array[org.apache.spark.sql.types.StructType]],
        classOf[org.apache.spark.sql.types.StructField],
        classOf[Array[org.apache.spark.sql.types.StructField]],
        Class.forName("org.apache.spark.sql.types.StringType$"),
        Class.forName("org.apache.spark.sql.types.LongType$"),
        Class.forName("org.apache.spark.sql.types.BooleanType$"),
        Class.forName("org.apache.spark.sql.types.DoubleType$"),
        classOf[org.apache.spark.sql.types.Metadata],
        classOf[org.apache.spark.sql.types.ArrayType],
        Class.forName("org.apache.spark.sql.execution.joins.UnsafeHashedRelation"),
        classOf[org.apache.spark.sql.catalyst.InternalRow],
        classOf[Array[org.apache.spark.sql.catalyst.InternalRow]],
        classOf[org.apache.spark.sql.catalyst.expressions.UnsafeRow],
        Class.forName("org.apache.spark.sql.execution.joins.LongHashedRelation"),
        Class.forName("org.apache.spark.sql.execution.joins.LongToUnsafeRowMap"),
        classOf[utils.BitSet],
        classOf[org.apache.spark.sql.types.DataType],
        classOf[Array[org.apache.spark.sql.types.DataType]],
        Class.forName("org.apache.spark.sql.types.NullType$"),
        Class.forName("org.apache.spark.sql.types.IntegerType$"),
        Class.forName("org.apache.spark.sql.types.TimestampType$"),
        Class.forName("org.apache.spark.internal.io.FileCommitProtocol$TaskCommitMessage"),
        Class.forName("scala.collection.immutable.Set$EmptySet$"),
        Class.forName("java.lang.Class")
      )
    )
  }

  /**
   * It generates a MOA CLI String for a ConceptDrift streams that recurrently repeated the given generators.
   *
   * @param generators The CLI String of the generators
   * @param numDrifts  The number of drifts to perform
   * @param every       The number of instances to perform each drift.
   * @param windowGradual For gradual changes, the number of instances within the change window. 1 for abrupt drifts
   * @return
   */
  def getCLIStringForConceptDrift(generators: Array[String], numDrifts: Int, every: Int, windowGradual: Int = 1): String = {
    def go(str: String, count: Int): String = {
      if(count > numDrifts){
        str
      } else {
        val conceptStr = if(count < numDrifts) "ConceptDriftStream -s (" + generators(count % generators.size) + ") -d (" else generators(count % generators.size) + ") "
        val newStr = go(str + conceptStr, count + 1)
        newStr + " -p " + every + " -w " + windowGradual + (if(count < numDrifts) ")" else "")
      }
    }

    val initStr = "ConceptDriftStream -s (" + generators.head + ") -d ("
    go(initStr, 1)

  }


  def getRandomPoints(interval: (Int,Int), num: Int, rand: JMetalRandom): Seq[Int] = {
    for(i <- 0 until num) yield rand.nextInt(interval._1, interval._2)
  }

  /**
   * It generates a MOA CLI String for a ConceptDrift streams that recurrently repeated the given generators.
   *
   * @param generators The CLI String of the generators
   * @param numDrifts  The number of drifts to perform
   * @param every       the number of instances to perform each drift. It is an interval of values where the change can occur randomly
   * @param windowGradual For gradual changes, the number of instances within the change window. 1 for abrupt drifts
   * @return
   */
  def getCLIStringForConceptDriftWithRandomDriftPoint(generators: Array[String], numDrifts: Int, points: Seq[Int], windowGradual: Int = 1): String = {
    def go(str: String, count: Int): String = {
      if(count > numDrifts){
        str
      } else {
        val conceptStr = if(count < numDrifts) "ConceptDriftStream -s (" + generators(count % generators.size) + ") -d (" else generators(count % generators.size) + ") "
        val newStr = go(str + conceptStr, count + 1)
        newStr + " -p " + points(count - 1) + " -w " + windowGradual + (if(count < numDrifts) ")" else "")
      }
    }

    val initStr = "ConceptDriftStream -s (" + generators.head + ") -d ("
    go(initStr, 1)

  }


  /**
   * It returns a copy of the given set of patterns without the attributes. It only keeps the class attribute.
   * @param p
   * @return
   */
  def cleanPatternAttributes(p: Seq[BinarySolution]): Seq[BinarySolution] = {
    val c = new Clase[BinarySolution]
    p.map(i => {
      val newI = i.copy().asInstanceOf[BinarySolution]
      val clazz = i.getAttribute(classOf[Clase[BinarySolution]]).asInstanceOf[Int]
      newI.getAttributes.clear()
      c.setAttribute(newI, clazz)
      newI //.copy().asInstanceOf[BinarySolution]
    })
  }

}
