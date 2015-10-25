package csw.services.pkg

import akka.actor.{ ActorLogging, Actor }

import scala.concurrent.Future
import Supervisor._

object LifecycleHandler {

  /**
   * Return type of LifecycleHandler methods that component actors can implement
   */
  sealed trait HandlerResponse

  case object Success extends HandlerResponse

  case class Failure(reason: String) extends HandlerResponse

}

/**
 * Containers and Components can override these to handle lifecycle changes.
 */
trait LifecycleHandler {
  this: Actor with ActorLogging ⇒

  import LifecycleHandler._
  import context.dispatcher

  /**
   * The name of the component (for error messages)
   */
  val name: String

  /**
   * This implements additional behavior (used in receive method of ccs controller actors)
   */
  protected def additionalReceive: Receive = {
    case Initialize ⇒
      for (
        result ← Future(initialize()) recover {
          case ex ⇒ Failure(ex.getMessage)
        }
      ) yield {
        val msg = result match {
          case Success         ⇒ Initialized(name)
          case Failure(reason) ⇒ InitializeFailed(name, reason)
        }
        context.parent ! msg
      }

    case Startup ⇒
      for (
        result ← Future(startup()) recover {
          case ex ⇒ Failure(ex.getMessage)
        }
      ) yield {
        val msg = result match {
          case Success         ⇒ Running(name)
          case Failure(reason) ⇒ StartupFailed(name, reason)
        }
        context.parent ! msg
      }

    case Shutdown ⇒
      for (
        result ← Future(shutdown()) recover {
          case ex ⇒ Failure(ex.getMessage)
        }
      ) yield {
        val msg = result match {
          case Success         ⇒ Initialized(name)
          case Failure(reason) ⇒ ShutdownFailed(name, reason)
        }
        context.parent ! msg
      }

    case Uninitialize ⇒
      for (
        result ← Future(uninitialize()) recover {
          case ex ⇒ Failure(ex.getMessage)
        }
      ) yield {
        val msg = result match {
          case Success         ⇒ Loaded(name)
          case Failure(reason) ⇒ UninitializeFailed(name, reason)
        }
        context.parent ! msg
      }
  }

  /**
   * Components can override this method to run code when initializing.
   * @return either the new lifecycle state, or the lifecycle error
   */
  def initialize(): HandlerResponse = {
    log.info(s"initialize $name")
    Success
  }

  /**
   * Components can override this method to run code when starting up.
   * @return either the new lifecycle state, or the lifecycle error
   */
  def startup(): HandlerResponse = {
    log.info(s"startup $name")
    Success
  }

  /**
   * Components can override this method to run code when shutting down.
   * @return either the new lifecycle state, or the lifecycle error
   */
  def shutdown(): HandlerResponse = {
    log.info(s"shutdown $name")
    Success
  }

  /**
   * Components can override this method to run code when uninitializing.
   * @return either the new lifecycle state, or the lifecycle error
   */
  def uninitialize(): HandlerResponse = {
    log.info(s"uninitialize $name")
    Success
  }
}

