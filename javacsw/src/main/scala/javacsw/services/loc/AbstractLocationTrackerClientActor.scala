package javacsw.services.loc

import akka.actor.{AbstractActor, ActorLogging}
import csw.services.loc.LocationService.Location
import csw.services.loc.LocationTrackerClientActor
import collection.JavaConverters._

/**
 * Java API abstract base class for an actor that wants to track connections with the location service.
 */
abstract class AbstractLocationTrackerClientActor extends AbstractActor with ActorLogging with LocationTrackerClientActor {

  /**
   * Don't override this from Java. Use the one below that takes a Java Set.
   * @param locations the resolved locations (of HCDs, etc.)
   */
  override def allResolved(locations: Set[Location]): Unit = allResolved(new java.util.HashSet(locations.asJavaCollection))

  /**
   * Called when all locations are resolved
   * @param locations the resolved locations (of HCDs, etc.)
   */
  def allResolved(locations: java.util.Set[Location]): Unit

}
