package csw.services.ccs

import java.util.Optional

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.ccs.CommandStatus._
import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}

import scala.compat.java8.OptionConverters._

/**
 * Executes the given configurations sequentially. On completion, the command originator should receive a
 * CommandResult message.
 */
class SequentialExecutor(commandHandler: ActorRef, sca: SetupConfigArg, commandOriginator: Option[ActorRef]) extends Actor with ActorLogging {

  import SequentialExecutor._

  override def receive: Receive = executingReceive(sca.configs, CommandStatus.CommandResults())

  // Start the first one
  commandHandler ! ExecuteOne(sca.configs.head, Some(self))

  private def executingReceive(configsIn: Seq[SetupConfig], execResultsIn: CommandResults): Receive = {

    case StopCurrentCommand =>
      // StopCurrentCommand passes this on to the actor receiving command messages
      log.info(s"Received stop on: $sca")
      commandHandler ! StopCurrentCommand

    case cs @ CommandStatus.Completed =>
      // Save record of sequential successes
      val execResultsOut = execResultsIn :+ CommandResultPair(cs, configsIn.head)
      val configsOut = configsIn.tail
      if (configsOut.isEmpty) {
        // If there are no more in the sequence, return the completion for all and be done
        // This returns the cumulative results to the original sender of the message to the sequenctial executor
        commandOriginator.foreach(_ ! CommandResult(sca.info.runId, AllCompleted, execResultsOut))
        context.stop(self)
      } else {
        // If there are more, start the next one and pass the completion status to the next execution
        context.become(executingReceive(configsOut, execResultsOut))
        commandHandler ! ExecuteOne(configsOut.head, Some(self))
      }

    case cs @ NoLongerValid(_) =>
      log.info(s"Validation Issue: $cs")
      // Save record of sequential successes
      val execResultsOut = execResultsIn :+ CommandResultPair(cs, configsIn.head)
      commandOriginator.foreach(_ ! CommandResult(sca.info.runId, Incomplete, execResultsOut))
      context.stop(self)

    case cs: Error =>
      log.info(s"Received error: ${cs.message}")
      // Save record of sequential successes
      val execResultsOut = execResultsIn :+ CommandResultPair(cs, configsIn.head)
      // This returns the cumulative results to the original sender of the message to the sequenctial executor
      commandOriginator.foreach(_ ! CommandResult(sca.info.runId, Incomplete, execResultsOut))
      context.stop(self)

    case cs: Invalid =>
      log.info(s"Received Invalid: ${cs.issue}")
      // Save record of sequential successes
      val execResultsOut = execResultsIn :+ CommandResultPair(cs, configsIn.head)
      commandOriginator.foreach(_ ! CommandResult(sca.info.runId, Incomplete, execResultsOut))
      context.stop(self)

    case cs @ Cancelled =>
      log.info(s"Received Cancelled")
      // Save record of sequential successes
      val execResultsOut = execResultsIn :+ CommandResultPair(cs, configsIn.head)
      commandOriginator.foreach(_ ! CommandResult(sca.info.runId, Incomplete, execResultsOut))
      context.stop(self)

    case x => log.error(s"SequentialExecutor:executingReceive received an unknown message: $x")
  }
}

// ---

object SequentialExecutor {

  /**
   * Used to create the actor.
   *
   * @param commandHandler actor responsible for executing the config, will receive an ExecuteOne message for each SetupConfig
   * @param sca contains the configurations
   * @param commandOriginator the actor that should receive a CommandResult message when all configs have completed or there was an error
   */
  def props(
    commandHandler:    ActorRef,
    sca:               SetupConfigArg,
    commandOriginator: Option[ActorRef]
  ): Props = Props(classOf[SequentialExecutor], commandHandler, sca, commandOriginator)

  /**
   * Java API: Used to create the actor.
   *
   * @param commandHandler actor responsible for executing the config, will receive an ExecuteOne message for each SetupConfig
   * @param sca contains the configurations
   * @param commandOriginator the actor that should receive a CommandResult message when all configs have completed or there was an error
   */
  def props(
    commandHandler:    ActorRef,
    sca:               SetupConfigArg,
    commandOriginator: Optional[ActorRef]
  ): Props = Props(classOf[SequentialExecutor], commandHandler, sca, commandOriginator.asScala)

  sealed trait SequenceExecutorMessages

  /**
   * The command handler receives messages of this type to execute a single SetupConfig
   * @param sc the setup config to execute
   * @param commandOriginator the original actor that sent the config, if known
   */
  case class ExecuteOne(sc: SetupConfig, commandOriginator: Option[ActorRef] = None) extends SequenceExecutorMessages

  /**
   * A message that can be sent to stop executing the current config
   */
  case object StopCurrentCommand extends SequenceExecutorMessages

  /**
   * Not currently used by this class. The vslice example uses this message in the command handler to indicate that a
   * command has started.
   */
  case object CommandStart extends SequenceExecutorMessages

}

