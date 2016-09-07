package csw.services.ccs

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.ccs.CommandStatus2._
import csw.services.ccs.SequentialExecution.SequentialExecutor.{Start, StartTheDamnThing, StarterFunction}
import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}

/**
 * TMT Source Code: 9/6/16.
 */
object SequentialExecution {

  class SequentialExecutor(sca: SetupConfigArg, doStart: StarterFunction, destination: ActorRef, replyTo: Option[ActorRef]) extends Actor with ActorLogging {

    import SequentialExecution._

    def receive = waitingReceive

    def waitingReceive: Receive = {
      case StartTheDamnThing =>
        val mysender = sender()
        val configs = sca.configs
        // Start the first one
        context.become(executingReceive(configs, CommandStatus2.ExecResults()))
        self ! Start(configs.head)
      case x => log.error(s"TromboneCommandHandler:waitingReceive received an unknown message: $x")
    }

    def executingReceive(configsIn: Seq[SetupConfig], execResultsIn: ExecResults): Receive = {

      case Start(sc: SetupConfig) =>
        log.info(s"Starting: $sc")
        doStart(sc, destination, Some(context.self))

      case cs @ NoLongerValid(issue) =>
        log.info(s"Received complete for cmd: $issue + $cs")
        // Save record of sequential successes
        context.become(receive)
        val execResultsOut = execResultsIn :+ (cs, configsIn.head)
        replyTo.foreach(_ ! CommandResult(sca.info.runId, Incomplete, execResultsOut))
        context.stop(self)

      case cs @ CommandStatus2.Completed =>

        // Save record of sequential successes
        val execResultsOut = execResultsIn :+ (cs, configsIn.head)
        val configsOut = configsIn.tail
        if (configsOut.isEmpty) {
          // If there are no more in the sequence, return the completion for all and be done
          context.become(receive)
          replyTo.foreach(_ ! CommandResult(sca.info.runId, Completed, execResultsOut))
          context.stop(self)
        } else {
          // If there are more, start the next one and pass the completion status to the next execution
          context.become(executingReceive(configsOut, execResultsOut))
          self ! Start(configsOut.head)
        }

      case cs: CommandStatus2.Error =>
        log.info(s"Received error: ${cs.message}")
        // Save record of sequential successes
        val execResultsOut = execResultsIn :+ (cs, configsIn.head)
        context.become(receive)
        replyTo.foreach(_ ! CommandResult(sca.info.runId, Incomplete, execResultsOut))
        context.stop(self)
    }
  }

  object SequentialExecutor {

    def props(sca: SetupConfigArg, doStart: StarterFunction, destination: ActorRef, replyTo: Option[ActorRef]): Props =
      Props(classOf[SequentialExecutor], sca, doStart, destination, replyTo)

    type StarterFunction = (SetupConfig, ActorRef, Option[ActorRef]) => Unit
    // Messages received by TromboneCommandHandler
    // TODO: Need to make this a SequenceConfigArg and add configs to SequenceConfigArg
    //case class ExecSequential(sca: SetupConfigArg, tromboneHCD: ActorRef, doStart: StarterFunction)
    case object StartTheDamnThing

    case class Start(sc: SetupConfig)
  }

}
