package csw.services.ls

import akka.actor._
import java.net.URI
import akka.actor.Identify
import LocationServiceActor._
import scala.concurrent.duration._

/**
 * Registers with the location service (which must be started as a separate process)
 * and re-registers again if the location service is restarted.
 */
object LocationServiceRegisterActor {

  /**
   * Used to create the actor.
   *
   * @param serviceId holds the name and service type of this actor
   * @param actorRef an optional reference to the actor for the service
   * @param configPath an optional path in a command service message that this actor is interested in (for HCD, Assembly)
   * @param httpUri an optional HTTP URI for the actor (if it provides an HTTP interface)
   */
  def props(serviceId: ServiceId, actorRef: Option[ActorRef], configPath: Option[String] = None,
            httpUri: Option[URI] = None): Props =
    Props(classOf[LocationServiceRegisterActor], serviceId, actorRef, configPath, httpUri)

  // Object passed in timer message to retry registration after location service connection lost
  private case object Retry

}

case class LocationServiceRegisterActor(serviceId: ServiceId, actorRef: Option[ActorRef],
                                        configPath: Option[String] = None,
                                        httpUri: Option[URI] = None) extends Actor with ActorLogging {

  import LocationServiceRegisterActor._

  identify()

  override def receive: Receive = {
    case x ⇒ log.error(s"Received unexpected message $x")
  }

  // Waiting for the location service actor ref
  def waitingForId: Receive = {
    case ActorIdentity(_, ref) ⇒ ref match {
      case Some(ls) ⇒ registerWithLocationService(ls)
      case None     ⇒ retryLater()
    }
    case Retry ⇒ identify()
  }

  // registered with the location service and watching it in case it restarts
  def registered(ls: ActorRef): Receive = {
    case Terminated(`ls`) ⇒ identify()
    case x                ⇒ log.error(s"Received unexpected message $x")
  }

  private def identify(): Unit = {
    implicit val system = context.system
    LocationService.getLocationService() ! Identify(0)
    context.become(waitingForId)
  }

  private def registerWithLocationService(ls: ActorRef): Unit = {
    log.info(s"Registering $serviceId ($configPath) with the location service ($actorRef)")
    val a = if (actorRef.isDefined) actorRef.get else self
    ls.tell(Register(serviceId, configPath, httpUri), a)
    context.watch(ls)
    context.become(registered(ls))
  }

  private def retryLater(): Unit = {
    implicit val dispatcher = context.system.dispatcher
    context.system.scheduler.scheduleOnce(1.second, self, Retry)
  }
}
