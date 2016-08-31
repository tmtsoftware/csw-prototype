package csw.services.loc

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.loc.LocationServiceProvider.Registration

import scala.concurrent.Future
import scala.util.{Failure, Success}

/*
object RegistrationTracker2 {
  /**
    * Used to create the RegistrationTracker actor
    *
    * @param registration Set of registrations to be registered with Location Service
    * @param replyTo      optional actorRef to reply to (default: parent of this actor)
    */
  def props(registration: Set[Registration], replyTo: Option[ActorRef] = None): Props = Props(classOf[RegistrationTracker2], registration, replyTo)
}

/**
  * An actor that tracks registration of one or more connections and replies with a
  * ComponentRegistered message when done.
  *
  * @param registration Set of registrations to be registered with Location Service
  * @param replyTo      optional actorRef to reply to (default: parent of this actor)
  */
case class RegistrationTracker2(c: LocationServiceProvider, registration: Set[Registration], replyTo: Option[ActorRef]) extends Actor with ActorLogging {
  import LocationServiceProvider._

  import context.dispatcher

  implicit val system = context.system

  val a = self
  Future.sequence(registration.toList.map(c.register)).onComplete {
    case Success(list) => registration.foreach { r =>
      val c = r.connection
      log.debug(s"Successful register of connection: $c")
      list.find(_.componentId == c.componentId).foreach { result =>
        replyTo.getOrElse(context.parent) ! ComponentRegistered(c, result)
      }
      system.stop(a)
    }
    case Failure(ex) =>
      val failed = registration.map(_.connection)
      log.error(s"Registration failed for $failed", ex)
      // XXX allan: Shoud an error message be sent to replyTo?
      system.stop(a)
  }

  def receive: Receive = {
    case x => log.error(s"Received unexpected message: $x")
  }
}
*/ 