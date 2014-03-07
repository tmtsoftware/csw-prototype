package org.tmt.csw.pkg

import akka.actor._
import com.typesafe.config.ConfigFactory

/**
 * Represents an OMOA (Observing Mode Oriented Architecture) Component, such as an assembly,
 * HCD (Hardware Control Daemon) or SC (Sequence Component).
 *
 * Each component has its own ActorSystem with a name that represents it
 */
object Component {

  /**
   * Creates a component actor with a new ActorSystem using the given props and name and returns the ActorRef
   * @param props used to create the actor
   * @param name the name of the component
   */
  def create(props: Props, name: String): ActorRef = {
    ActorSystem(name).actorOf(props, name)
  }

  /**
   * Lifecycle state messages
   */
  sealed trait ComponentLifecycleState
  case object Initialize extends ComponentLifecycleState
  case object Startup extends ComponentLifecycleState
  case object Running extends ComponentLifecycleState
  case object Shutdown extends ComponentLifecycleState
  case object Uninit extends ComponentLifecycleState
  case object Remove extends ComponentLifecycleState
}

trait   Component {
  this: Actor with ActorLogging =>

  import Component._

  /**
   * The component name
   */
  val name: String

  // Receive component messages
  def receiveComponentMessages: Receive = {
    case Initialize => initialize()
    case Startup => startup()
    case Running => run()
    case Shutdown => shutdown()
    case Uninit => uninit()
    case Remove => remove()

    case Terminated(actorRef) => terminated(actorRef)
  }

  def initialize(): Unit
  def startup(): Unit
  def run(): Unit
  def shutdown(): Unit
  def uninit(): Unit
  def remove(): Unit

  /**
   * Called when the given child actor terminates
   */
  def terminated(actorRef: ActorRef): Unit = log.info(s"Actor $actorRef terminated")
}
