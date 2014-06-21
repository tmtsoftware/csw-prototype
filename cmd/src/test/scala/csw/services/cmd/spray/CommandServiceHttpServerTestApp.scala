package csw.services.cmd.spray

import akka.actor._
import csw.services.cmd.akka._
import csw.services.ls.LocationServiceActor
import LocationServiceActor.{ServicesReady, ServiceType, ServiceId, LocationServiceInfo}
import java.net.URI

/**
 * Standalone command service test application.
 *
 * Running this class starts an HTTP server (configured in resources/reference.conf under testCommandService)
 * that runs the command service REST interface.
 * You can then submit a configuration in JSON format with some HTTP client. For example:
 *
 * curl -H "Accept: application/json" -H "Content-type: application/json" -X POST -d "$json" http://$host:$port/submit
 *
 * where $json is a telescope configuration in JSON (or the simplified Akka config format).
 * The return value is the runId in JSON format, for example: {"runId": "373b13ec-c31b-446f-8482-6adebe64bcb0"}.
 * You can use the runId to get the command status:
 *
 * curl http://$host:$port/status/$runId
 *
 * The return value looks something like this:
 *
 * {
 * "name": "Complete",
 * "runId": "373b13ec-c31b-446f-8482-6adebe64bcb0",
 * "message": ""
 * }
 */
object CommandServiceHttpServerTestApp extends App {
  /**
   * Construct the ActorSystem we will use in our application
   */
  implicit lazy val system = ActorSystem("commandServiceApp")
  implicit val dispatcher = system.dispatcher

  /**
   * Ensure that the constructed ActorSystem is shut down when the JVM shuts down
   */
  sys.addShutdownHook(system.shutdown())

  val interface = CommandServiceTestSettings(system).interface
  val port = CommandServiceTestSettings(system).port
  val timeout = CommandServiceTestSettings(system).timeout
  system.actorOf(CommandServiceHttpServer.props(getCommandServiceActor(), interface, port, timeout), "commandService")

  // Test HCD
  object TestHcdCommandServiceActor {
    /**
     * Props to create the test HCD actor
     * @param configPath the config keys that this HCD is interested in
     * @param numberOfSecondsToRun number of seconds for processing the config
     * @param name name of the ConfigActor to create
     */
    def props(configPath: String, numberOfSecondsToRun: Int, name: String): Props =
      Props(classOf[TestHcdCommandServiceActor], configPath, numberOfSecondsToRun, name)
  }

  class TestHcdCommandServiceActor(configPath: String, numberOfSecondsToRun: Int, name: String)
    extends CommandServiceActor with OneAtATimeCommandQueueController {
    override val configActor = context.actorOf(TestConfigActor.props(commandStatusActor, numberOfSecondsToRun), name)

    override def receive: Receive = receiveCommands
  }

  //  // Test assembly
  object TestAssemblyCommandServiceActor {
    // Note: for testing we pass in the list of HCDs. Normally we would request them from the location service.
    def props(hcds: List[LocationServiceInfo]): Props = Props(classOf[TestAssemblyCommandServiceActor], hcds)
  }

  class TestAssemblyCommandServiceActor(hcds: List[LocationServiceInfo]) extends AssemblyCommandServiceActor with OneAtATimeCommandQueueController {
    override def receive: Receive = receiveCommands

    configDistributorActor ! ServicesReady(hcds)
  }


  /**
   * Creates and returns a new CommandServiceActor
   * @param n a unique number (needed if multiple command servers are running at once)
   * @param numberOfSecondsToRun number of seconds the worker actors should run
   * @return the actor ref of the assembly command server
   */
  def getCommandServiceActor(n: Int = 1, numberOfSecondsToRun: Int = 2): ActorRef = {
    val hcdA = system.actorOf(TestHcdCommandServiceActor.props("tmt.tel.base.pos", numberOfSecondsToRun, s"TestConfigActorA$n"),
      name = s"HCD-A$n")

    val hcdB = system.actorOf(TestHcdCommandServiceActor.props("tmt.tel.ao.pos.one", numberOfSecondsToRun, s"TestConfigActorB$n"),
      name = s"HCD-B$n")

    // Normally this information would come from the location service, but for testing it is hard coded here
    val hcds = List(
      LocationServiceInfo(
        ServiceId("HCD-A", ServiceType.HCD), List(new URI(hcdA.path.toString)),
        Some("tmt.tel.base.pos"), Some(hcdA)),
      LocationServiceInfo(
        ServiceId("HCD-B", ServiceType.HCD), List(new URI(hcdB.path.toString)),
        Some("tmt.tel.ao.pos.one"), Some(hcdB))
    )

    system.actorOf(TestAssemblyCommandServiceActor.props(hcds), name = s"Assembly$n")
  }
}
