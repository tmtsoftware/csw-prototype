package org.tmt.csw.cmd.spray

import akka.actor._
import org.tmt.csw.cmd.akka._
import org.tmt.csw.cmd.akka.ConfigRegistrationActor._

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
  system.actorOf(Props[AppActor])

  class AppActor extends Actor with ActorLogging {
    system.actorOf(CommandServiceHttpServer.props(getCommandServiceActor(), interface, port, timeout), "commandService")

    override def receive: Receive = {
      case Registered(actorRef) =>
        log.debug(s"Received registered ack from $actorRef")
    }
  }

  // Test HCD object
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

  // Test HCD class
  class TestHcdCommandServiceActor(configPath: String, numberOfSecondsToRun: Int, name: String)
    extends CommandServiceActor with OneAtATimeCommandQueueController {
    override val configActor = context.actorOf(TestConfigActor.props(commandStatusActor, numberOfSecondsToRun), name)
    override val configPaths = Set(configPath)

    override def receive: Receive = receiveCommands
  }

  // Test assembly
  class TestAssemblyCommandServiceActor extends AssemblyCommandServiceActor with OneAtATimeCommandQueueController {
    override def receive: Receive = receiveCommands
  }

  /**
   * Creates and returns a new CommandServiceActor
   * @param n a unique number (needed if multiple command servers are running at once)
   * @param numberOfSecondsToRun number of seconds the worker actors should run
   * @return the actor ref of the assembly command server
   */
  def getCommandServiceActor(n: Int = 1, numberOfSecondsToRun: Int = 2): ActorRef = {
    val assembly = system.actorOf(Props[TestAssemblyCommandServiceActor], name = s"Assembly$n")

    val hcdA = system.actorOf(TestHcdCommandServiceActor.props("config.tmt.tel.base.pos", numberOfSecondsToRun, s"TestConfigActorA$n"),
      name = s"HCD-A$n")

    val hcdB = system.actorOf(TestHcdCommandServiceActor.props("config.tmt.tel.ao.pos.one", numberOfSecondsToRun, s"TestConfigActorB$n"),
      name = s"HCD-B$n")

    hcdA ! RegisterRequest(assembly)
    hcdB ! RegisterRequest(assembly)
    assembly
  }
}
