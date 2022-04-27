package problem

import genetic.individual.EPMBinarySolution
import net.sourceforge.jFuzzyLogic.membership.MembershipFunction
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.uma.jmetal.problem.BinaryProblem
import org.uma.jmetal.solution.BinarySolution
import org.uma.jmetal.util.binarySet.BinarySet
import org.uma.jmetal.util.pseudorandom.JMetalRandom
import org.uma.jmetal.util.pseudorandom.impl.JavaRandomGenerator
import org.uma.jmetal.util.solutionattribute.impl.{NumberOfViolatedConstraints, OverallConstraintViolation}
import problem.attributes.{Attribute, Clase}
import problem.evaluator.EPMEvaluator
import utils.BitSet
import weka.core.Instances
import weka.core.converters.ArffLoader

import java.io.{BufferedReader, FileReader, StringReader}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
  * Class that defines an Emerging Pattern problem in JMetal framework
  *
  */
class EPMProblem extends BinaryProblem with Serializable {


  val RANDOM_INITIALISATION: Int = 0
  val ORIENTED_INITIALISATION: Int = 1
  val COVERAGE_INITIALISATION: Int = 2

  /**
    * The datasets. It contains all the information about the instances
    */
  protected var dataset: DataFrame = _
  /* DATAFRAME FOR BIG DATA USING SPARK */
  protected var data: Instances = _

  /**
    * The attributes information
    */
  protected var attributes: Seq[Attribute] = _
  protected var schema: StructType =  new StructType

  /**
    * The class of the problem for the extraction of rules.
    */
  protected val clase: Clase[BinarySolution] = new Clase[BinarySolution]()
  protected var clas: Int = 0


  /**
    * The number of objectives to be used in a MOEA algorithm.
    */
  protected var numObjectives: Int = 2


  /**
    * The number of linguistic labels to be used in numeric variables
    */
  protected var numLabels: Int = 3


  /**
    * The initialisation method used
    */
  protected var initialisationMethod: Int = 1

  /**
    * The seed of the random number generator
    */
  protected var seed: Int = 1

  /**
    * The fuzzy sets stored for each numeric variable
    */
  protected var fuzzySets: Seq[Seq[MembershipFunction]] = _


  /**
    * The number of violated constraints in the problem
    */
  var numberOfViolatedConstraints: NumberOfViolatedConstraints[BinarySolution] = _

  /**
    * The overall constraint violation
    */
  var overallConstraintViolation: OverallConstraintViolation[BinarySolution] = _

  /**
    * The random number generator
    */
  var rand: JMetalRandom = _
  var rnd = new JavaRandomGenerator(seed)

  /**
    * The Spark Session employed in this problem
    */
  var spark: SparkSession = _

  /**
    * The number of examples in the dataset
    */
  var numExamples: Int = 0

  /**
    * The number of partitions in the dataset
    */
  protected var numPartitions: Int = 0

  /**
    * The default comment char for the ARFF reading as CSV
    */
  protected var commentChar = "@"

  /**
    * The null value string
    */
  protected var nullValue = "?"


  /**
   * Should generate individuals that only belongs to one class?
   */
  protected var fixedClass: Boolean = false;


  protected var evaluator: EPMEvaluator = _


  /**
   * Set all new generated individual to belong to the given class.
   * @param clas
   */
  def fixClass(clas: Int): Unit = {
    fixedClass = true
    this.clas = clas
  }


  def setRandomGenerator(generator : JMetalRandom) = {
    rand = generator
  }

  def setNullValue(value: String): Unit = {nullValue = value}

  /**
    * Get the corresponding fuzzy set j for the variable i
    * @param i
    * @param j
    * @return
    */
  def getFuzzySet(i: Int, j: Int): MembershipFunction = {
    fuzzySets(i)(j)
  }

  /**
    * It returns the membership degree of the value x to the fuzzy set j of the variable i
    * @param i
    * @param j
    * @param x
    */
  def membershipDegree(i: Int, j: Int, x: Double): Double ={
    fuzzySets(i)(j).membership(x)
  }

  def getFuzzyVariable(i: Int): Seq[MembershipFunction] ={
    fuzzySets(i)
  }


  def getDataset: DataFrame = dataset

  def getData: Instances = data

  def setData( d: Instances): Unit = data = d

  def getAttributes: Seq[Attribute] = attributes

  def getNumPartitions(): Int = numPartitions

  def getNumLabels(): Int = numLabels

  def setNumLabels(labels: Int) = {numLabels = labels}

  def getInitialisationMethod(): Int = initialisationMethod

  def getNumExamples: Int  = numExamples

  def setInitialisationMethod(method: Int) = initialisationMethod = method

  def setSparkSession(sp: SparkSession) = {
    this.spark = sp
  }

  override def getNumberOfBits(index: Int): Int = {
    if(attributes(index).isNumeric){
      numLabels
    } else {
      attributes(index).numValues
    }
  }

  override def getTotalNumberOfBits: Int = {
    var suma = 0
    for(i <- 0 until attributes.length){
      suma += getNumberOfBits(i)
    }
    suma
  }

  override def getNumberOfVariables: Int = attributes.length - 1

  override def getNumberOfObjectives: Int = numObjectives

  override def getNumberOfConstraints: Int = 0

  override def getName: String = "Emerging Pattern Mining"

  override def evaluate(solution: BinarySolution): Unit = ???

  override def createSolution(): BinarySolution = {

    // Create a random individual
    val sol: BinarySolution = if (initialisationMethod == RANDOM_INITIALISATION) { // By default, individuals are initialised at random
      //new DefaultBinarySolution(this)
      new EPMBinarySolution(this)
    } else if (initialisationMethod == ORIENTED_INITIALISATION) { // Oriented initialisation
      OrientedInitialisation(0.25)
      //new DefaultBinarySolution(this)
    }  else {
      new EPMBinarySolution(this)
    }



    clase.setAttribute(sol, clas)

    // The next individual belongs to a different class (last attribute), this is for ensuring we have individuals for all classes.
    if(!fixedClass)
      clas = (clas + 1) % attributes.last.numValues

    return sol
  }


  def setNumberOfObjectives(number: Int): Unit = {numObjectives = number}

  //  def createSolution(sets: ArrayBuffer[(Int, Int)], pctVariables: Double): BinarySolution = {
//
//    val sol = new DefaultBinarySolution(this)
//    val maxVariablesToInitialise = Math.ceil(pctVariables * sets.length)
//    val varsToInit = rand.nextInt(1, maxVariablesToInitialise.toInt + 1)
//
//    val initialised = new BitSet(sets.length)
//    var varInitialised = 0
//
//    while(varInitialised != varsToInit){
//      val value = rand.nextInt(0 , sets.length - 1) // la variable sets corresponde a los pares var,value que cubren al ejemplo.
//      if (!initialised.get(value)){
//        val set = new BinarySet(sol.getNumberOfBits(sets(value)._1))
//        set.set(sets(value)._2)
//
//        // check if the generated variable is empty and fix it if necessary
//        if(set.cardinality() == 0){
//          set.set(rand.nextInt(0, sol.getNumberOfBits(sets(value)._1 ) - 1))
//        } else  if(set.cardinality() == sol.getNumberOfBits(sets(value)._1)){
//          set.clear(rand.nextInt(0, sol.getNumberOfBits(sets(value)._1) - 1))
//        }
//
//        sol.setVariableValue(sets(value)._1,set)
//        varInitialised += 1
//        initialised.set(value)
//      }
//    }
//
//    // clear the non-initialised variables
//    for(i <- 0 until getNumberOfVariables){
//      if(!initialised.get(i)){
//        sol.getVariableValue(i).clear()
//      }
//    }
//
//    return sol
//  }


  def getNumberOfClasses: Int = {attributes.last.numValues}

  /**
   * It reads an ARFF file and it stores it as an `Instances` class, i.e., as a set of instances
   *
   * @param path the path of the file to be read
   *
   * @note By default, the class is set as the last variable of the dataset!
   */
  def readDataset(path: String): Unit ={
    val reader = new BufferedReader(new FileReader(path))
    val arff = new ArffLoader.ArffReader(reader)

    // Read the data, set class
    data = arff.getData
    data.setClassIndex(data.numAttributes - 1)

    // Get the attributes information
    attributes = generateAttributes()

    // Generate the fuzzy sets (if necessary)
    generateFuzzySets()

    numExamples = data.numInstances()
  }


  def readDatasetFromRDD_String(rdd: RDD[String], header: String): Unit = {

    //rdd.cache()
    //numExamples = rdd.count().toInt

    var builder = new StringBuilder(header + "\n")
    val dat = rdd.mapPartitions(x => {
      val builder = new StringBuilder("")
      x.foreach(s => builder ++= (s + '\n'))
      Array(builder).iterator
    }).reduce((x,y) => x.append(y))
    /*.treeReduce((x, y) => {
      x.append(y)
    }, Evaluator.TREE_REDUCE_DEPTH)*/

    builder = builder.append(dat)

    val reader = new StringReader(builder.toString())
    val arff = new ArffLoader.ArffReader(reader)

    // Read the data, set class
    data = arff.getData
    data.setClassIndex(data.numAttributes - 1)
    numExamples = data.numInstances()
    // Get the attributes information
    // attributes = generateAttributes()

    // Generate the fuzzy sets (if necessary)
    //generateFuzzySets()

    //numExamples = data.numInstances()
  }

  /**
    * It reads a dataset in ARFF format and it stores it as a Spark DataFrame for Big Data Processing
    * @param path
    * @param numPartitions
    * @param spark
    * @return
    */
  def readDatasetBigDataAsDataFrame(path: String, numPartitions: Int, spark: SparkSession): Unit = {
    this.spark = spark
    this.numPartitions = numPartitions
    val listValues = spark.sparkContext.textFile(path, this.numPartitions)
      .filter(x => x.startsWith("@"))
      .filter(x => !x.toLowerCase.startsWith("@relation"))
      .filter(x => !x.toLowerCase.startsWith("@data"))
      .filter(x => !x.toLowerCase.startsWith("@inputs"))
      .filter(x => !x.toLowerCase.startsWith("@outputs"))
      .map(x => {
      val values = x.split("(\\s*)(\\{)(\\s*)|(\\s*)(\\})(\\s*)|(\\s*)(\\[)(\\s*)|(\\s*)(\\])(\\s*)|(\\s*)(,)(\\s*)|\\s+")
      if (values(2).equalsIgnoreCase("numeric") | values(2).equalsIgnoreCase("real") | values(2).equalsIgnoreCase("integer")) {
        StructField(values(1), DoubleType, true)
      } else {
        StructField(values(1), StringType, true)
      }
    }).coalesce(this.numPartitions).collect()

    val schema = StructType(listValues)


      dataset = spark.read.option("header", "false")
        .option("comment", "@")
        .option("nullValue", nullValue)
        .option("mode", "FAILFAST") // This mode throws an error on any malformed line is encountered
        .schema(schema).csv(path).coalesce(this.numPartitions)


    dataset = zipWithIndex(dataset,0)

    numExamples = dataset.count().toInt

  }



  /**
    * It returns an array with the attribute definitions from a spark DataFrame
    *
    * @param spark The active Spark session
    * @return
    */
  def generateAttributes(spark: SparkSession): Unit = {


      attributes = dataset.columns.drop(1).map(x => {
        val nominal = dataset.schema(x).dataType match {
          case StringType => true
          case DoubleType => false
        }

        import spark.implicits._
        val attr = if (nominal) {
          val values = dataset.select(x).distinct().map(x => x.getString(0)).collect()
          new Attribute(x, nominal,0,0,0,values.toList)
        } else {
          val a = dataset.select(min(x), max(x)).head()
          new Attribute(x, nominal, a.getDouble(0), a.getDouble(1), numLabels, null)
        }
        attr
      })

  }

  /**
   * It returns an array with the attribute definition from a WEKA dataset
   */
  def generateAttributes(): Seq[Attribute] = {
    val atts = new ArrayBuffer[Attribute]()
    for (i <- 0 until data.numAttributes()) {
      if (data.attribute(i).isNumeric) {
        //Convert numeric variable
        // calculate max
        val max = data.attributeStats(i).numericStats.max

        //calculate min
        val min = data.attributeStats(i).numericStats.min
        schema = schema.add(data.attribute(i).name(), DoubleType, true)
        atts += new Attribute(data.attribute(i).name(), false, min, max, numLabels, null)
      } else {
        //Convert nominal variable
        val a: List[Any] = enumerationAsScalaIterator(data.attribute(i).enumerateValues()).toList
        schema = schema.add(data.attribute(i).name(), StringType, true)
        atts +=  new Attribute(data.attribute(i).name(), true, 0, 0, 0, a)
      }
    }
    atts
  }

  /**
    * It generates the fuzzy sets definitions for numeric variables.
    *
   * @note By default, uniform partitions with triangular membership functions are created with `numLabels` function
    */
  def generateFuzzySets(): Unit ={
    if (attributes != null) {

      fuzzySets = attributes.map(attr => {
        if(attr.isNominal){
          null
        } else {
          Fuzzy.generateTriangularLinguisticLabels(attr.min, attr.max, numLabels)
        }
      })

    }
  }

  def updateFuzzySets(variable: Int, min: Double, max: Double) = {

  }

  /**
    * It generates a random indivual initialising a percentage of its variables at random.
    * @param rand
    * @return
    */
  def OrientedInitialisation(pctVariables: Double): BinarySolution = {
    val sol = new EPMBinarySolution(this)
    val maxVariablesToInitialise = Math.round(pctVariables * getNumberOfVariables)
    val varsToInit = rand.nextInt(1, maxVariablesToInitialise.toInt)
    val initialised = new BitSet(getNumberOfVariables)
    var varInitialised = 0

    while(varInitialised != varsToInit){
      val value = rand.nextInt(0 , getNumberOfVariables - 1)
      if (!initialised.get(value)){
        val set = new BinarySet(sol.getNumberOfBits(value))
        for(i <- 0 until sol.getNumberOfBits(value)){
          if(rand.nextDouble(0.0, 1.0) <= 0.5)
            set.set(i)
          else
            set.clear(i)
        }
        // check if the generated variable is empty and fix it if necessary
        if(set.cardinality() == 0){
          set.set(rand.nextInt(0, sol.getNumberOfBits(value) - 1))
        } else  if(set.cardinality() == sol.getNumberOfBits(value)){
          set.clear(rand.nextInt(0, sol.getNumberOfBits(value) - 1))
        }

        sol.setVariableValue(value,set)
        varInitialised += 1
        initialised.set(value)
      }
    }

    // clear the non-initialised variables
    for(i <- 0 until getNumberOfVariables){
      if(!initialised.get(i)){
        sol.getVariableValue(i).clear()
      }
    }

    return sol
  }


  /**
    * zipWithIndex: it adds a column in the data frame that corresponds the index of that element
   * TODO: method udf is deprecated in Spark 3.0, we must change this.
    * @param df
    * @param offset
    * @param indexName
    * @return
    */
  def zipWithIndex(df: DataFrame, offset: Long = 1, indexName: String = "index") = {
    val columnNames = Array(indexName) ++ df.columns
    //df.repartition(this.numPartitions)
    val dfWithPartitionId = df.withColumn("partition_id", spark_partition_id()).withColumn("inc_id", monotonically_increasing_id())

    val partitionOffsets = dfWithPartitionId
      .groupBy("partition_id")
      .agg(count(lit(1)) as "cnt", first("inc_id") as "inc_id")
      .orderBy("partition_id")
      .select(sum("cnt").over(Window.orderBy("partition_id")) - col("cnt") - col("inc_id") + lit(offset) as "cnt" )
      .collect()
      .map(_.getLong(0))
      .toArray

    dfWithPartitionId
      .withColumn("partition_offset", udf((partitionId: Int) => partitionOffsets(partitionId), LongType)(col("partition_id")))
      .withColumn(indexName, col("partition_offset") + col("inc_id"))
      .drop("partition_id", "partition_offset", "inc_id")
      .select(columnNames.head, columnNames.tail: _*).cache()
  }



  def showDataset() = {
    this.dataset.show()
  }


  /**
    * Performs a coverage-based initialisation.
    *
    * It tries to generate an individual that cover the given example with at most {@code pctVariables} of its variables initialised.
    *
    * @param example
    * @param evaluator
    * @param pctVariables
    * @return
    */
  def coverageBasedInitialisation(example: Int, evaluator: EPMEvaluator, pctVariables: Double): BinarySolution = {

    val sets: Seq[(Int, Int)] = evaluator.getPairsCoveringAnExample(example)

    val sol: BinarySolution = new EPMBinarySolution(this)

    val maxVariablesToInitialise = Math.ceil(pctVariables * sets.length)
    val varsToInit = rand.nextInt(1, maxVariablesToInitialise.toInt + 1)

    val initialised = new BitSet(sets.length)
    var varInitialised = 0

    while(varInitialised != varsToInit){
      val value = rand.nextInt(0 , sets.length - 1) // la variable sets corresponde a los pares var,value que cubren al ejemplo.
      if (!initialised.get(value)){
        val set = new BinarySet(sol.getNumberOfBits(sets(value)._1))
        set.set(sets(value)._2)

        // check if the generated variable is empty and fix it if necessary
        if(set.cardinality() == 0){
          set.set(rand.nextInt(0, sol.getNumberOfBits(sets(value)._1 ) - 1))
        } else  if(set.cardinality() == sol.getNumberOfBits(sets(value)._1)){
          set.clear(rand.nextInt(0, sol.getNumberOfBits(sets(value)._1) - 1))
        }

        sol.setVariableValue(sets(value)._1,set)
        varInitialised += 1
        initialised.set(value)
      }
    }

    // clear the non-initialised variables
    for(i <- 0 until getNumberOfVariables){
      if(!initialised.get(i)){
        sol.getVariableValue(i).clear()
      }
    }

    // Set the class
    clase.setAttribute(sol, clas)

    // The next individual belongs to a different class (last attribute), this is for ensuring we have individuals for all classes.
    if(!fixedClass)
      clas = (clas + 1) % attributes.last.numValues


    return sol
  }
}
