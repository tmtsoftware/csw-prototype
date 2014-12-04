package csw.services.pkg

import akka.actor._

/**
 * The Lifecycle Manager is an actor that deals with component lifecycle messages
 * so components don't have to. There is one Lifecycle Manager per component.
 * It registers with location service and is responsible for starting and stopping the component.
 * All component messages go through the Lifecycle Manager, so it can reject any
 * messages that are not allowed in a given lifecycle.
 */
object LifecycleManager {

  sealed trait LifecycleCommand

  case object Start extends LifecycleCommand

  case object Stop extends LifecycleCommand

  // XXX add Restart?

  sealed trait LifecycleState

  case class Started(name: String, actorRef: ActorRef) extends LifecycleState

  case class Stopped(name: String) extends LifecycleState

  def props(componentProps: Props, name: String): Props = Props(classOf[LifecycleManager], componentProps, name)
}

/**
 * A lifecycle manager actor that manages the component actor given by the arguments.
 *
 * @param props used to create the component actor (HCD, assembly) being managed
 * @param name the name of the component (used to register with the location service)
 */
case class LifecycleManager(props: Props, name: String) extends Actor with ActorLogging {

  import LifecycleManager._

  override def receive: Receive = stopped

  def stopped: Receive = {
    case Start         ⇒ start(Some(sender()))
    case Stop          ⇒ log.error(s"$name is already stopped")
    case Terminated(_) ⇒ log.info(s"$name has stopped")
    case msg           ⇒ log.error(s"$name is not running: Ignoring message $msg")
  }

  def stopping(replyTo: ActorRef): Receive = {
    case Start ⇒ self ! Start // XXX?
    case Stop  ⇒ log.error(s"$name is already stopping")
    case Terminated(_) ⇒
      replyTo ! Stopped(name)
      context.become(stopped)
    case msg ⇒ log.error(s"$name is not running: Ignoring message $msg")
  }

  def started(actorRef: ActorRef): Receive = {
    case Start ⇒ log.error(s"$name is already started")
    case Stop  ⇒ stop(actorRef)
    case Terminated(_) ⇒
      log.info(s"$name terminated: restarting")
      start(None)
    case msg ⇒ forwardMessage(msg)
  }

  def start(replyTo: Option[ActorRef]): Unit = {
    log.info(s"Starting $name")
    val actorRef = context.actorOf(props, name)
    context.become(started(actorRef))
    context.watch(actorRef)
    replyTo.map(_ ! Started(name, self))
  }

  //  def stop(actorRef: ActorRef): Unit = {
  //    log.info(s"Stopping $name")
  //    context.become(stopped)
  //    context.stop(actorRef)
  //    sender() ! Stopped(name)
  //  }

  def stop(actorRef: ActorRef): Unit = {
    log.info(s"Stopping $name")
    context.become(stopping(sender()))
    context.stop(actorRef)
  }

  def forwardMessage(msg: Any): Unit = context.child(name) match {
    case Some(actorRef) ⇒
      actorRef.tell(msg, sender())
    case None ⇒
      log.error(s"$name is not running. Ignoring message: $msg")
  }
}
