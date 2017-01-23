package javacsw.services.events

import java.util.concurrent.CompletableFuture

import akka.actor.ActorRefFactory
import csw.services.events.EventServiceAdmin
import scala.compat.java8.FutureConverters._

import scala.concurrent.ExecutionContext

object JEventServiceAdmin {
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
   * @param ec required for futures
   * @return a future that completes when the redis server exits
   */
  def startEventService(name: String, noExit: Boolean, ec: ExecutionContext): CompletableFuture[Unit] = {
    EventServiceAdmin.startEventService(name, noExit)(ec).toJava.toCompletableFuture
  }
}

/**
 * Implements the java admin API for Event Service
 */
class JEventServiceAdmin(eventService: IEventService, system: ActorRefFactory) extends IEventServiceAdmin {

  import system.dispatcher
  implicit val sys = system

  val eventAdmin = EventServiceAdmin(eventService.asInstanceOf[JEventService].eventService)

  override def shutdown(): CompletableFuture[Unit] = eventAdmin.shutdown().toJava.toCompletableFuture
  override def reset(): CompletableFuture[Unit] = eventAdmin.reset().toJava.toCompletableFuture
}
