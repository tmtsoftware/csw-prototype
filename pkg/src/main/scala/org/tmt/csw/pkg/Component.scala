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

  /** =Lifecycle state messages=
    *
    * From TMT.CTR.ICD.13.002.DRF06:
    *
    * - INIT: the system should perform any initialization needed.
    * When complete, the system is in the Initialized lifecycle state.
    *
    * - STARTUP: the system should bring any hardware online and be ready to execute commands.
    * Once completed, the system is in the Running state and the READY mode.
    * No motions should take place during the startup transition to the Running state.
    * All functional behavior is available once the system is in the Running state.
    *
    * - RUNNING: result of STARTUP message: go to the READY state
    *
    * - SHUTDOWN: all changes made to move from Initialized to Running should be reversed.
    * If actions are underway, the actions should first be completed.
    *
    * - UNINIT: all changes made to move from Loaded state to Initialized should be reversed.
    *
    * - REMOVE: software may be optionally removed if possible or necessary to move to the Ready state.
    */
  sealed trait ComponentLifecycleState

  case object Init extends ComponentLifecycleState
  case object Startup extends ComponentLifecycleState
  case object Running extends ComponentLifecycleState
  case object Shutdown extends ComponentLifecycleState
  case object Uninit extends ComponentLifecycleState
  case object Remove extends ComponentLifecycleState
}

trait Component {
  this: Actor with ActorLogging =>

  import Component._

  /**
   * The component name
   */
  val name: String

  // Receive component messages
  def receiveComponentMessages: Receive = {
    case Init => initialize()
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

/**
 * An assembly is a component and may optionally also extend CommandServiceActor (or AssemblyCommandServiceActor)
 */
trait Assembly extends Component {
  this: Actor with ActorLogging =>
}

/**
 * An HCD is a component and may optionally also extend CommandServiceActor
 */
trait Hcd extends Component {
  this: Actor with ActorLogging =>
}
