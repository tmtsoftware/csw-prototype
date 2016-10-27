package csw.services.events

import akka.actor.ActorRefFactory
import csw.services.trackLocation.TrackLocation

import scala.concurrent.{ExecutionContext, Future}

/**
 * Admin API for the event service.
 */
object EventServiceAdmin {
  /**
   * Starts a redis instance on a random free port (redis-server must be in your shell path)
   * and registers it with the location service.
   * This is the equivalent of running this from the command line:
   *   {{{
   *   tracklocation --name "Event Service Test" --command "redis-server --port %port" --no-exit
   *   }}}
   *
   * This method is mainly for use by tests. In production, you would use the tracklocation app
   * to start Redis once.
   *
   * @param name The name to use to register the event service with the location service
   * @param noExit if true, do not exit the application when redis exists
   * @return a future that completes when the redis server exits
   */
  def startEventService(name: String = EventService.defaultName, noExit: Boolean = true)(implicit ec: ExecutionContext): Future[Unit] = {
    val ne = if (noExit) List("--no-exit") else Nil
    val args = List("--name", name, "--command", "redis-server --port %port") ++ ne
    Future {
      TrackLocation.main(args.toArray)
    }
  }

  def apply(eventService: EventService)(implicit system: ActorRefFactory): EventServiceAdmin =
    EventServiceAdminImpl(eventService)
}

/**
 * Defines the admin API for Event Service
 */
trait EventServiceAdmin {
  /**
    * For use in testing: Deletes all keys in all databases in the Redis instance
    */
  def reset(): Future[Unit]

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  def shutdown(): Future[Unit]
}

/**
 * Provides admin level methods for working with the Event Service database.
 *
 * @param eventService used to access the event service
 */
private[events] case class EventServiceAdminImpl(eventService: EventService)(implicit system: ActorRefFactory)
    extends EventServiceAdmin {

  import system.dispatcher

  val redisClient = eventService.asInstanceOf[EventServiceImpl].redisClient

  override def reset(): Future[Unit] = {
    redisClient.flushall().map(_ => ())
  }


  override def shutdown(): Future[Unit] = {
    val f = redisClient.shutdown()
    redisClient.stop()
    f.map(_ => ()).recover { case _ => () }
  }
}

