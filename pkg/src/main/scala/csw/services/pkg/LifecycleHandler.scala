package csw.services.pkg

import akka.actor.{ ActorLogging, Actor }
import csw.services.pkg.LifecycleManager._

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/**
 * Containers and Components can override these to handle lifecycle changes.
 * Add "orElse receiveLifecycleCommands" to the receive method to use the
 * methods here. The handler methods all return a future, which if successful
 * causes a reply to be sent with the new state. If the future failed, a failed
 * message is sent as a reply.
 */
trait LifecycleHandler {
  this: Actor with ActorLogging ⇒
  import context.dispatcher
  val name: String

  def receiveLifecycleCommands: Receive = {
    case Initialize ⇒
      initialize().onComplete {
        case Success(_)  ⇒ context.parent ! Initialized(name)
        case Failure(ex) ⇒ context.parent ! InitializeFailed(name, ex)
      }

    case Startup ⇒
      startup().onComplete {
        case Success(_)  ⇒ context.parent ! Running(name)
        case Failure(ex) ⇒ context.parent ! StartupFailed(name, ex)
      }

    case Shutdown ⇒
      shutdown().onComplete {
        case Success(_)  ⇒ context.parent ! Initialized(name)
        case Failure(ex) ⇒ context.parent ! ShutdownFailed(name, ex)
      }

    case Uninitialize ⇒
      uninitialize().onComplete {
        case Success(_)  ⇒ context.parent ! Loaded(name)
        case Failure(ex) ⇒ context.parent ! UninitializeFailed(name, ex)
      }
  }

  def initialize(): Future[Unit] = Future { log.debug(s"initialize") }

  def startup(): Future[Unit] = Future { log.debug(s"startup") }

  def shutdown(): Future[Unit] = Future { log.debug(s"shutdown") }

  def uninitialize(): Future[Unit] = Future { log.debug(s"uninitialize") }
}

