package csw.services.events

import akka.actor.ActorRefFactory
import csw.services.trackLocation.TrackLocation

import scala.concurrent.{ExecutionContext, Future}

/**
 * Admin API for the telemetry service.
 */
object TelemetryServiceAdmin {
  /**
   * Starts a redis instance on a random free port (redis-server must be in your shell path)
   * and registers it with the location service.
   * This is the equivalent of running this from the command line:
   *   {{{
   *   tracklocation --name "Telemetry Service Test" --command "redis-server --port %port" --no-exit
   *   }}}
   *
   * This method is mainly for use by tests. In production, you would use the tracklocation app
   * to start Redis once.
   *
   * @param name The name to use to register the telemetry service with the location service
   * @param noExit if true, do not exit the application when redis exists
   * @return a future that completes when the redis server exits
   */
  def startTelemetryService(name: String = TelemetryService.defaultName, noExit: Boolean = true)(implicit ec: ExecutionContext): Future[Unit] = {
    val ne = if (noExit) List("--no-exit") else Nil
    val args = List("--name", name, "--command", "redis-server --port %port") ++ ne
    Future {
      TrackLocation.main(args.toArray)
    }
  }

  def apply(telemetryService: TelemetryService)(implicit system: ActorRefFactory): TelemetryServiceAdmin =
    TelemetryServiceAdminImpl(telemetryService)
}

/**
 * Defines the admin API for Telemetry Service
 */
trait TelemetryServiceAdmin {
  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  def shutdown(): Future[Unit]
}

/**
 * Provides admin level methods for working with the Telemetry Service database.
 *
 * @param telemetryService used to access the telemetry service
 */
private[events] case class TelemetryServiceAdminImpl(telemetryService: TelemetryService)(implicit system: ActorRefFactory)
    extends TelemetryServiceAdmin {

  import system.dispatcher

  val redisClient = telemetryService.asInstanceOf[TelemetryServiceImpl].redisClient

  override def shutdown(): Future[Unit] = {
    val f = redisClient.shutdown()
    redisClient.stop()
    f.map(_ => ()).recover { case _ => () }
  }
}

