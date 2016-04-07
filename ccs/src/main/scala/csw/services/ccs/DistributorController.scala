package csw.services.ccs

/*
import akka.actor.{ Props, Actor, ActorLogging }
import akka.util.Timeout
import akka.pattern.ask
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.LocationService.{ Disconnected, ServicesReady, ResolvedService }
import csw.services.loc.{ LocationService, ServiceRef }
import csw.util.cfg.Configurations.SetupConfigArg
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

/**
 * A controller that forwards lists of configs (SetupConfigArg) to assemblies based on
 * the prefix (as registered with the location service).
 *
 * The list of possible target assemblies must be passed in the serviceRefs argument.
 *
 * Once all the replies are in from the assemblies, a single command status message
 * is sent back to the sender (Either Completed or, if any of the commands failed,
 * an Error message).
 *
 * An error command status is also returned to the sender if the replies are not received
 * in the given timeout period.
 */
object DistributorController {
  /**
   * Used to create the DistributorController actor
   * @param serviceRefs set of services required
   * @param timeout Timeout while waiting for all configs to complete (default: 10 secs)
   * @return
   */
  def props(serviceRefs: Set[ServiceRef], timeout: Timeout = Timeout(10.seconds)): Props =
    Props(classOf[DistributorController], serviceRefs, timeout)

}

protected case class DistributorController(serviceRefs: Set[ServiceRef], timeout: Timeout) extends Actor with ActorLogging {

  // Start the location service actor
  context.actorOf(LocationService.props(serviceRefs))

  override def receive = waitingForServices

  def waitingForServices: Receive = {
    case config: SetupConfigArg ⇒ log.warning(s"Ignoring config since services not connected: $config")

    case ServicesReady(map)     ⇒ context.become(connected(map))

    case Disconnected           ⇒

    case x                      ⇒ log.warning(s"Received unexpected message: $x")
  }

  def connected(services: Map[ServiceRef, ResolvedService]): Receive = {
    case config: SetupConfigArg ⇒ process(services, config)

    case ServicesReady(map)     ⇒ context.become(connected(map))

    case Disconnected           ⇒ context.become(waitingForServices)

    case x                      ⇒ log.warning(s"Received unexpected message: $x")
  }

  /**
   * Called to process the configs and reply to the sender with the command status
   * @param services contains information about any required services
   * @param configArg contains a list of configurations
   */
  private def process(services: Map[ServiceRef, ResolvedService], configArg: SetupConfigArg): Unit = {
    implicit val t = timeout
    import context.dispatcher
    val replyTo = sender()
    Future.sequence(for {
      config ← configArg.configs
      service ← services.values.find(v ⇒ v.prefix == config.configKey.prefix && v.serviceRef.accessType == AkkaType)
      actorRef ← service.actorRefOpt
    } yield {
      (actorRef ? config).mapTo[CommandStatus]
    }).onComplete {
      case Success(commandStatusList) ⇒
        if (commandStatusList.exists(_.isFailed)) {
          val msg = commandStatusList.filter(_.isFailed).map(_.message).mkString(", ")
          replyTo ! CommandStatus.Error(configArg.info.runId, msg)
        } else {
          replyTo ! CommandStatus.Completed(configArg.info.runId)
        }
      case Failure(ex) ⇒
        replyTo ! CommandStatus.Error(configArg.info.runId, ex.getMessage)
    }
  }
}
*/

