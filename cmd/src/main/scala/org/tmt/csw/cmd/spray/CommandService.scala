package org.tmt.csw.cmd.spray

import akka.actor._
import akka.io.IO
import scala.concurrent.duration.FiniteDuration
import spray.can.Http
import spray.routing.HttpServiceActor
import org.tmt.csw.cmd.akka.{RunId, CommandStatus}
import akka.actor.OneForOneStrategy

/**
 * Messages and `akka.actor.Props` factories for the CommandService actor.
 * (Original algorithm based on Spray example at http://hseeberger.github.io/blog/2013/07/22/gabbler-part3/)
 */
object CommandService {

  /**
   * Function completing a request when given a list of CommandStatus objects.
   */
  type Completer = Option[CommandStatus] => Unit

  /**
   * Factory for `akka.actor.Props` for CommandService.
   */
  def props(commandServiceActor: ActorRef, interface: String, port: Int, timeout: FiniteDuration): Props =
    Props(new CommandService(commandServiceActor, interface, port, timeout))
}

/**
 * A service providing a REST-ful API to the command service actor
 */
case class CommandService(commandServiceActor: ActorRef, interface: String, port: Int, timeout: FiniteDuration)
  extends HttpServiceActor with CommandServiceRoute {

  IO(Http)(context.system) ! Http.Bind(self, interface, port)

  override def receive: Receive = runRoute(route)

  // creates a new CommandServiceMonitor actor to listen for status messages for the given runId
  override def newMonitorFor(runId: RunId): ActorRef = {
    context.actorOf(CommandServiceMonitor.props(timeout), monitorName(runId))
  }

  // Gets an existing CommandServiceMonitor actor for the given runId
  override def getMonitorFor(runId: RunId): Option[ActorRef] = {
    context.child(monitorName(runId))
  }

  // Gets the name of the CommandServiceMonitor actor for the given runId
  private def monitorName(runId: RunId): String = {
    s"CommandServiceMonitor-${runId.id}"
  }

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() {
      case _ => SupervisorStrategy.Stop
    }
}
