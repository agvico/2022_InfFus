import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should
import org.uma.jmetal.solution.BinarySolution
import org.uma.jmetal.util.comparator.ObjectiveComparator
import org.uma.jmetal.util.pseudorandom.JMetalRandom
import problem.EPMStreamingProblem
import problem.evaluator.EPMStreamingEvaluator
import utils.Utils


class Test extends AnyFlatSpec with should.Matchers {

  val problem = new EPMStreamingProblem
  val generator = Utils.createDataGenerator("generators.SEAGenerator -b")
  val evaluator = new EPMStreamingEvaluator(0)
  val rnd = JMetalRandom.getInstance()
  rnd.setSeed(1)

  lazy val data = for (i <- 0 until 5000) yield generator.nextInstance().getData
  problem.readDataset(data, generator.getHeader)
  problem.setRandomGenerator(rnd)
  problem.setNumLabels(3)
  problem.generateFuzzySets()

  evaluator.setBigDataProcessing(false)
  evaluator.initialise(problem)



  "A chromosome comparator " must "return the individual with highest fitness" in {
    val a = problem.createSolution()
    val b = problem.createSolution()

    a.setObjective(0, 0.4)
    b.setObjective(0, 0.6)

    val selection = new ObjectiveComparator[BinarySolution](0, ObjectiveComparator.Ordering.DESCENDING)
    selection.compare(a, b) must be (1)
  }



}
