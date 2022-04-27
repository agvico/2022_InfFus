package problem.federated.messages
import akka.actor.typed.ActorRef
import org.uma.jmetal.solution.BinarySolution

/** Trait defining a message that sends the device. The device responds to serverCommands messages */
sealed trait DeviceCommand

// NOTE: The use of `from` with type 'ActorRef[ServerCommand]' is due to devices ar Actors whose behaviours are
// ruled by the ServerCommands, i.e., it works by reacting to server messages.

/** TODO: instead of sending Seq[BinarySolution] we should send Seq[BitSet] for serialisation performance.
 * BinarySolution need to serialize the problem as it is a field (therefore, there is a bottleneck. */

/** It sends the current extracted pattern to the server in order to create the global, federated model */
final case class SendPatterns(patterns: Seq[BinarySolution], from: ActorRef[ServerCommand]) extends DeviceCommand

/** It asks the server for the global model as the quality of the own set is poor  */
final case class AskForPatterns(from: ActorRef[ServerCommand]) extends DeviceCommand

/** It ask the server to join the federated network */
final case class JoinToNetwork(from: ActorRef[ServerCommand]) extends DeviceCommand

/** It tells the server that the computation is done. */
final case class COMPUTATION_DONE(from: ActorRef[ServerCommand]) extends DeviceCommand

/**
 * It tells the server to write the quality of the global model in a file.
 * This is to perform comparison against different datasets as the number of aggregation rounds varies.
 * @param from
 */
final case class WRITE_RESULTS(from: ActorRef[ServerCommand]) extends DeviceCommand

final case class DEVICE_ACK(message: String) extends DeviceCommand
final case class DEVICE_ERROR(message: String) extends  DeviceCommand
