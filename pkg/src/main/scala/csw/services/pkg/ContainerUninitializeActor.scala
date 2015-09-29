package csw.services.pkg

import akka.actor.{ ActorLogging, Actor, Props }
import LifecycleManager._

// Used to uninitialize components and then wait for confirmation before exiting or restarting
private[pkg] object ContainerUninitializeActor {
  def props(components: Map[String, Component.ComponentInfo], exit: Boolean): Props =
    Props(classOf[ContainerUninitializeActor], components, exit)
}

private[pkg] class ContainerUninitializeActor(components: Map[String, Component.ComponentInfo], exit: Boolean)
    extends Actor with ActorLogging {

  // Subscribe to Loaded lifecycle state messages from all components
  components.foreach {
    case (name, info) ⇒
      info.lifecycleManager ! LifecycleManager.SubscribeToLifecycleStates()
      info.lifecycleManager ! LifecycleManager.Uninitialize
  }

  context.become(receiveState(components.keys.toList))

  // not used
  override def receive: Receive = {
    case _ ⇒
  }

  def receiveState(componentsLeft: List[String]): Receive = {
    case LifecycleStateChanged(state, error, connected) if state.isLoaded ⇒
      checkDone(componentsLeft.filter(_ != state.name))
  }

  def checkDone(componentsLeft: List[String]): Unit = {
    if (componentsLeft.isEmpty) {
      if (exit) System.exit(0) else context.parent ! Startup
      context.stop(self)
    } else
      context.become(receiveState(componentsLeft))
  }
}
