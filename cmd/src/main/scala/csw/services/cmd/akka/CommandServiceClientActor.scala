package csw.services.cmd.akka

import akka.actor._
import csw.shared.cmd.RunId
import scala.concurrent.duration._
import scala.Some

object CommandServiceClientActor {

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
  import csw.services.cmd.akka.CommandServiceActor._
  import csw.services.cmd.akka.CommandQueueActor._
  import csw.services.cmd.akka.ConfigActor._

  // Receive messages
  override def receive: Receive = {
    // Queue related commands
    case Submit(config, _)          ⇒ sender() ! submitCommand(config)
    case QueueBypassRequest(config) ⇒ sender() ! requestCommand(config)
    case m @ QueueStop              ⇒ commandServiceActor forward m
    case m @ QueuePause             ⇒ commandServiceActor forward m
    case m @ QueueStart             ⇒ commandServiceActor forward m
    case m @ QueueDelete(runId)     ⇒ commandServiceActor forward m
    case m @ ConfigCancel(runId)    ⇒ commandServiceActor forward m
    case m @ ConfigAbort(runId)     ⇒ commandServiceActor forward m
    case m @ ConfigPause(runId)     ⇒ commandServiceActor forward m
    case m @ ConfigResume(runId)    ⇒ commandServiceActor forward m
    case m @ ConfigGet(config)      ⇒ commandServiceActor forward m

    case x                          ⇒ log.error(s"Unknown CommandServiceClientActor message: $x")
  }
}

