package csw.services.pkg

import akka.actor.{ ActorLogging, Actor }
import csw.services.pkg.LifecycleManager._

import scala.concurrent.Future

/**
 * Containers and Components can override these to handle lifecycle changes.
 * Add "orElse receiveLifecycleCommands" to the receive method to use the
 * methods here. The handler methods all return either the new state, if successful,
 * or an error status, on failure, which is sent to the parent actor (the lifecycle manager).
 * If a future fails with an exception, a failed message is automatically sent.
 */
trait LifecycleHandler {
  this: Actor with ActorLogging ⇒

  import context.dispatcher

  val name: String

  def receiveLifecycleCommands: Receive = {
    case Initialize ⇒
      for (
        result ← Future(initialize()) recover {
          case ex ⇒ Left(InitializeFailed(name, ex.getMessage))
        }
      ) yield {
        context.parent ! result.merge
      }

    case Startup ⇒
      for (
        result ← Future(startup()) recover {
          case ex ⇒ Left(StartupFailed(name, ex.getMessage))
        }
      ) yield {
        context.parent ! result.merge
      }

    case Shutdown ⇒
      for (
        result ← Future(shutdown()) recover {
          case ex ⇒ Left(ShutdownFailed(name, ex.getMessage))
        }
      ) yield {
        context.parent ! result.merge
      }

    case Uninitialize ⇒
      for (
        result ← Future(uninitialize()) recover {
          case ex ⇒ Left(UninitializeFailed(name, ex.getMessage))
        }
      ) yield {
        context.parent ! result.merge
      }
  }

  /**
   * Components can override this method to run code when initializing.
   * @return either the new lifecycle state, or the lifecycle error
   */
  def initialize(): Either[InitializeFailed, Initialized] = {
    log.info(s"initialize $name")
    Right(Initialized(name))
  }

  /**
   * Components can override this method to run code when starting up.
   * @return either the new lifecycle state, or the lifecycle error
   */
  def startup(): Either[StartupFailed, Running] = {
    log.info(s"startup $name")
    Right(Running(name))
  }

  /**
   * Components can override this method to run code when shutting down.
   * @return either the new lifecycle state, or the lifecycle error
   */
  def shutdown(): Either[ShutdownFailed, Initialized] = {
    log.info(s"shutdown $name")
    Right(Initialized(name))
  }

  /**
   * Components can override this method to run code when uninitializing.
   * @return either the new lifecycle state, or the lifecycle error
   */
  def uninitialize(): Either[UninitializeFailed, Loaded] = {
    log.info(s"uninitialize $name")
    Right(Loaded(name))
  }
}

