package javacsw.services.loc

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import csw.services.loc.{Connection, LocationService}
import LocationService._

import scala.concurrent.Future

import csw.services.loc.LocationTrackerWorker
import LocationTrackerWorker._

import collection.JavaConverters._

/**
 * Scala side Java API support for the location service.
 */
object JLocationServiceSup {

  /**
   * For Java API: Convenience method that gets the location service information for a given set of services.
   *
   * @param connections set of requested connections
   * @param system      the caller's actor system
   * @return a future object describing the services found
   */
  def resolve(connections: java.util.Set[Connection], system: ActorSystem, timeout: Timeout): Future[LocationsReady] = {
    LocationService.resolve(connections.asScala.toSet)(system, timeout)
  }

  /**
   * For Java API: Used to create the RegistrationTracker actor
   *
   * @param registration Set of registrations to be registered with Location Service
   * @param replyTo      optional actorRef to reply to (default: parent of this actor)
   */
  def registrationTrackerProps(registration: java.util.Set[Registration], replyTo: ActorRef): Props =
    RegistrationTracker.props(registration.asScala.toSet, Some(replyTo))

  /**
   * Used to create the LocationTracker actor
   *
   * @param replyTo optional actorRef to reply to (default: parent of this actor)
   */
  def locationTrackerProps(replyTo: ActorRef): Props =
    LocationTracker.props(Some(replyTo))
}
