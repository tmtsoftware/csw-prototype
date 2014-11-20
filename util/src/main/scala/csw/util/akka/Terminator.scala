package csw.util.akka

import akka.actor.{ Terminated, ActorLogging, Actor, ActorRef }

/**
 * Exits the application when the given actor stops
 * @param ref reference to the main actor of an application
 */
class Terminator(ref: ActorRef) extends Actor with ActorLogging {
  context watch ref

  def receive = {
    case Terminated(_) ⇒
      log.info("{} has terminated, shutting down system", ref.path)
      context.system.shutdown()
  }
}
