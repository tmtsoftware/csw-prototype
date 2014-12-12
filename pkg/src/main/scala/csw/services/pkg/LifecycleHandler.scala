package csw.services.pkg

import akka.actor.{ ActorLogging, Actor }

import scala.concurrent.Future
import scala.util.{ Failure, Success }

import csw.services.pkg.LifecycleManager._

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
      val replyTo = sender()
      initialize().onComplete {
        case Success(_)  ⇒ replyTo ! Initialized(name)
        case Failure(ex) ⇒ replyTo ! InitializeFailed(name, ex)
      }

    case Startup ⇒
      val replyTo = sender()
      startup().onComplete {
        case Success(_)  ⇒ replyTo ! Running(name)
        case Failure(ex) ⇒ replyTo ! StartupFailed(name, ex)
      }

    case Shutdown ⇒
      val replyTo = sender()
      shutdown().onComplete {
        case Success(_)  ⇒ replyTo ! Initialized(name)
        case Failure(ex) ⇒ replyTo ! ShutdownFailed(name, ex)
      }

    case Uninitialize ⇒
      val replyTo = sender()
      uninitialize().onComplete {
        case Success(_)  ⇒ replyTo ! Loaded(name)
        case Failure(ex) ⇒ replyTo ! UninitializeFailed(name, ex)
      }
  }

  def initialize(): Future[Unit] = Future {}

  def startup(): Future[Unit] = Future {}

  def shutdown(): Future[Unit] = Future {}

  def uninitialize(): Future[Unit] = Future {}
}

