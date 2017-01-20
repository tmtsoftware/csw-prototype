package csw.services.pkg

import csw.services.pkg.LifecycleManager._

import scala.concurrent.Future

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
 *
 * @deprecated This class will be removed in a future version
 */
@Deprecated
trait LifecycleHandler {
  this: Component =>

  import LifecycleHandler._
  import context.dispatcher

  val componentName: String = self.path.name

  /**
   * This implements additional behavior (used in receive method of ccs controller actors)
   */
  def lifecycleHandlerReceive: Receive = {
    case Initialize =>
      val manager = sender()
      for (
        result <- Future(initialize()) recover {
          case ex => Failure(ex.getMessage)
        }
      ) yield {
        val msg = result match {
          case Success         => InitializeSuccess
          case Failure(reason) => InitializeFailure(reason)
        }
        manager ! msg
      }

    case Startup =>
      val manager = sender()
      for (
        result <- Future(startup()) recover {
          case ex => Failure(ex.getMessage)
        }
      ) yield {
        val msg = result match {
          case Success         => StartupSuccess
          case Failure(reason) => StartupFailure(reason)
        }
        manager ! msg
      }

    case Shutdown =>
      val manager = sender()
      for (
        result <- Future(shutdown()) recover {
          case ex => Failure(ex.getMessage)
        }
      ) yield {
        val msg = result match {
          case Success         => ShutdownSuccess
          case Failure(reason) => ShutdownFailure(reason)
        }
        manager ! msg
      }

    case Uninitialize =>
      val manager = sender()
      for (
        result <- Future(uninitialize()) recover {
          case ex => Failure(ex.getMessage)
        }
      ) yield {
        val msg = result match {
          case Success         => UninitializeSuccess
          case Failure(reason) => UninitializeFailure(reason)
        }
        manager ! msg
      }

    case LifecycleFailure(state: LifecycleState, reason: String) =>
      val manager = sender()
      // No result
      Future(lifecycleFailure(state, reason))
      manager ! Success
  }

  /**
   * Components can override this method to run code when initializing.
   *
   * @return either the new lifecycle state, or the lifecycle error
   */
  def initialize(): HandlerResponse = {
    log.debug(s"initialize $componentName")
    Success
  }

  /**
   * Components can override this method to run code when starting up.
   *
   * @return either the new lifecycle state, or the lifecycle error
   */
  def startup(): HandlerResponse = {
    log.debug(s"startup $componentName")
    Success
  }

  /**
   * Components can override this method to run code when shutting down.
   *
   * @return either the new lifecycle state, or the lifecycle error
   */
  def shutdown(): HandlerResponse = {
    log.debug(s"shutdown $componentName")
    Success
  }

  /**
   * Components can override this method to run code when uninitializing.
   *
   * @return either the new lifecycle state, or the lifecycle error
   */
  def uninitialize(): HandlerResponse = {
    log.debug(s"uninitialize $componentName")
    Success
  }

  /**
   * Components can override this method to run code when a lifecycle failure has occurred.
   *
   * @return nothing
   */
  def lifecycleFailure(lifecycleState: LifecycleState, reason: String): Unit = {
    log.debug(s"lifecycleFailure for $componentName going to state $lifecycleState failed for reason: $reason")
  }

}
