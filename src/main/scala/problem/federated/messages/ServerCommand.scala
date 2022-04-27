package problem.federated.messages

import akka.actor.typed.ActorRef
import org.uma.jmetal.solution.BinarySolution


/** Trait for the representation of server commands */
sealed trait ServerCommand

/** TODO: instead of sending Seq[BinarySolution] we should send Seq[BitSet] for serialisation performance.
 * BinarySolution need to serialize the problem as it is a field (therefore, there is a bottleneck. */

  /** It */
  final case class ResponseWithPatterns(patterns: Seq[BinarySolution]) extends ServerCommand

  final case class DoComputation(replyTo: ActorRef[DeviceCommand]) extends ServerCommand

  final case class SERVER_ACK(message: String) extends ServerCommand

  final case class SERVER_ERROR(message: String) extends ServerCommand


