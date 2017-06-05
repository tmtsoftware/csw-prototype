package csw.examples

import akka.actor.ActorRef
import csw.services.ccs.Validation._
import csw.services.ccs.{AssemblyController, HcdController}
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService.ResolvedAkkaLocation
import csw.services.loc._
import csw.services.pkg.Component.{AssemblyInfo, RegisterOnly}
import csw.services.pkg.Supervisor.{Initialized, Running}
import csw.services.pkg.{Assembly, Supervisor}
import csw.util.itemSet.ItemSets.Setup

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Class that implements the assembly actor
 *
 * @param info contains information about the assembly and the components it depends on
 */
class AssemblyExample(override val info: AssemblyInfo, supervisor: ActorRef) extends Assembly with AssemblyController {
  // The HCD actor (located via the location service)
  private var hcd: ActorRef = _

  // This tracks the HCD
  private val trackerSubscriber = context.actorOf(LocationSubscriberActor.props)
  trackerSubscriber ! LocationSubscriberActor.Subscribe
  LocationSubscriberActor.trackConnections(info.connections, trackerSubscriber)

  override def receive: Receive = controllerReceive orElse {

    // Receive the HCD's location
    case l: ResolvedAkkaLocation =>
      if (l.actorRef.isDefined) {
        hcd = l.actorRef.get
        log.info(s"Got actorRef: $hcd")
        supervisor ! Initialized
      }

    case Running =>
      log.debug("received Running")

    case x => log.error(s"Unexpected message: ${x.getClass}")
  }

  /**
   * Validates a received config arg
   */
  private def validateSequenceConfigArg(s: Setup): Validation = {
    if (s.itemSetKey.prefix != HCDExample.prefix) {
      Invalid(WrongConfigKeyIssue("Wrong prefix"))
    } else {
      val missing = s.missingKeys(HCDExample.rateKey)
      if (missing.nonEmpty)
        Invalid(MissingKeyIssue(s"Missing keys: ${missing.mkString(", ")}"))
      else Valid
    }
  }

  override def setup(s: Setup, commandOriginator: Option[ActorRef]): Validation = {
    // Returns validations for all
    val validation = validateSequenceConfigArg(s)
    if (validation == Valid) {
      // For this trivial test we just forward the configs to the HCD
      hcd ! HcdController.Submit(s)
    }
    validation
  }
}

/**
 * Starts Hcd as a standalone application.
 */
object AssemblyExampleApp extends App {
  println("Starting Assembly1")
  LocationService.initInterface()
  val assemblyName = "assemblyExample"
  val className = "csw.examples.AssemblyExample"
  val componentId = ComponentId(assemblyName, ComponentType.Assembly)
  val targetHcdConnection = AkkaConnection(ComponentId(HCDExample.hcdName, ComponentType.HCD))
  val hcdConnections: Set[Connection] = Set(targetHcdConnection)
  val prefix = "tcs.mobie.blue.filter"
  val assemblyInfo = AssemblyInfo(assemblyName, prefix, className, RegisterOnly, Set(AkkaType), hcdConnections)
  val (supervisorSystem, supervisor) = Supervisor.create(assemblyInfo)

  // The code below shows how you could shut down the assembly
  if (false) {
    import supervisorSystem.dispatcher
    supervisorSystem.scheduler.scheduleOnce(15.seconds) {
      Supervisor.haltComponent(supervisor)
      Await.ready(supervisorSystem.whenTerminated, 5.seconds)
      System.exit(0)
    }
  }
}
