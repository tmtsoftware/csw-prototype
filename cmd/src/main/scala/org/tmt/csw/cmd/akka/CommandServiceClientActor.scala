package org.tmt.csw.cmd.akka

import org.tmt.csw.cmd.core.Configuration
import akka.actor._
import scala.concurrent.duration._
import org.tmt.csw.cmd.akka.CommandServiceMessage._
import org.tmt.csw.cmd.akka.CommandServiceMessage.ConfigResume
import org.tmt.csw.cmd.akka.CommandServiceMessage.ConfigAbort
import org.tmt.csw.cmd.akka.CommandServiceMessage.QueueDelete
import org.tmt.csw.cmd.akka.CommandServiceMessage.ConfigCancel
import org.tmt.csw.cmd.akka.CommandServiceMessage.ConfigPause
import scala.Some

object CommandServiceClientActor {
  // Message sent to get the command status for the given runId
  case class GetStatus(runId: RunId)

  // Used to create the actor
  def props(commandServiceActor: ActorRef, timeout: FiniteDuration): Props =
    Props(classOf[CommandServiceClientActor], commandServiceActor, timeout)
}

/**
 * Sits between the CommandServiceClient and the CommandServiceActor. Used to present a simpler API to the client.
 */
class CommandServiceClientActor(val commandServiceActor: ActorRef, val timeout: FiniteDuration)
    extends CommandServiceActorClientHelper {

  import CommandServiceClientActor._

  // Receive messages
  override def receive: Receive = {
    // Queue related commands
    case Submit(config, _) => sender ! submitCommand(config)
    case QueueBypassRequest(config) => sender ! requestCommand(config)
    case GetStatus(runId) => checkCommandStatus(runId, getCompleter(sender))
    case QueueStop => queueStop()
    case QueuePause => queuePause()
    case QueueStart => queueStart()
    case QueueDelete(runId) => queueDelete(runId)
    case ConfigCancel(runId) => configCancel(runId)
    case ConfigAbort(runId) => configAbort(runId)
    case ConfigPause(runId) => configPause(runId)
    case ConfigResume(runId) => configResume(runId)

    case x => log.error(s"Unknown CommandServiceClientActor message: $x")

  }

  // Returns a function that takes a command status and sends it to the given actor
  private def getCompleter(ref: ActorRef): CommandServiceActorClientHelper.Completer = {
    case Some(status) => ref ! status
    case None =>
  }
}

