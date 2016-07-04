package csw.services.asconsole

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import csw.services.loc.LocationService
import ch.qos.logback.classic._
import csw.services.alarms.AlarmModel.{AlarmStatus, SeverityLevel}
import csw.services.alarms.{AlarmJson, AlarmKey, AlarmService, AlarmServiceSetSeverityActor}
import org.slf4j.LoggerFactory
import AlarmService.Problem

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A command line application that locates the Redis instance used for the Alarm Service (using the Location Service)
 * and performs tasks based on the command line options, such as initialize or display the list of alarms.
 */
object AsConsole extends App {
  LocationService.initInterface()

  // Needed for use with Futures
  implicit val system = ActorSystem("AsConsole")

  // Timeout when waiting for a future
  implicit val timeout = Timeout(60.seconds)

  /**
   * Command line options ("asconsole --help" prints a usage message with descriptions of all the options)
   * See val parser below for descriptions of the options.
   */
  private case class Options(
    asName:          Option[String] = None, // Alarm Service name
    asConfig:        Option[File]   = None, // Alarm Service Config File (ASCF)
    listAlarms:      Boolean        = false,
    shutdown:        Boolean        = false,
    subsystem:       Option[String] = None,
    component:       Option[String] = None,
    name:            Option[String] = None, // Alarm name (with wildcards)
    severity:        Option[String] = None,
    expire:          Option[Int]    = Some(15),
    refreshSeverity: Boolean        = false,
    monitorAlarms:   Boolean        = false,
    acknowledge:     Boolean        = false,
    logLevel:        Option[String] = Some("OFF"),
    noExit:          Boolean        = false
  )

  // XXX TODO: Add options for --list output format: pdf, html, json, config, text?

  // XXX TODO: Add option to set/display severity for an alarm

  // Parses the command line options
  private val parser = new scopt.OptionParser[Options]("asconsole") {
    head("asconsole", System.getProperty("CSW_VERSION"))

    opt[String]("as-name") valueName "<name>" action { (x, c) ⇒
      c.copy(asName = Some(x))
    } text "The name that was used to register the Alarm Service Redis instance (Default: 'Alarm Service')"

    opt[File]("init") valueName "<alarm-service-config-file>" action { (x, c) ⇒
      c.copy(asConfig = Some(x))
    } text "Initialize the set of available alarms from the given Alarm Service Config File (ASCF)"

    opt[Unit]("list").action((_, c) ⇒
      c.copy(listAlarms = true)).text("Prints a list of all alarms (See other options to filter what is printed)")

    opt[Unit]("shutdown").action((_, c) ⇒
      c.copy(shutdown = true)).text("Shuts down the Alarm Service Redis instance")

    opt[String]("subsystem") valueName "<subsystem>" action { (x, c) ⇒
      c.copy(subsystem = Some(x))
    } text "Limits the alarms returned by --list to the given subsystem"

    opt[String]("component") valueName "<name>" action { (x, c) ⇒
      c.copy(component = Some(x))
    } text "Limits the alarms returned by --list to the given component (subsystem must also be specified)"

    opt[String]("name") valueName "<name>" action { (x, c) ⇒
      c.copy(name = Some(x))
    } text "Limits the alarms returned by --list to those whose name matches the given value (may contain Redis wildcards)"

    opt[String]("severity") valueName "<severity>" action { (x, c) ⇒
      c.copy(severity = Some(x))
    } text "Sets the severity level for the alarm given by (--subsystem, --component, --name) to the given level (Alarm must be unique)"

    opt[Unit]("monitor") action { (x, c) ⇒
      c.copy(monitorAlarms = true)
    } text "Starts monitoring changes in the severity of alarm(s) given by (--subsystem, --component, --name) (may contain Redis wildcards)"

    opt[Unit]("acknowledge") action { (x, c) ⇒
      c.copy(acknowledge = true)
    } text "Acknowledge the alarm given by (--subsystem, --component, --name) (Alarm must be unique)"

    opt[Unit]("refresh") action { (x, c) ⇒
      c.copy(refreshSeverity = true)
    } text "Continually refresh the given alarm's severity before it expires (use together with --subsystem, --component, --name, --severity)"

    opt[Int]("expire") action { (x, c) ⇒
      c.copy(expire = Some(x))
    } text "Number of secs before a key's severity expires (default 15, must be >= 3)"

    opt[Unit]("no-exit") action { (x, c) ⇒
      c.copy(noExit = true)
    } text "For testing: prevents application from exiting the JVM"

    opt[String]("log") valueName "<log-level>" action { (x, c) ⇒
      c.copy(logLevel = Some(x))
    } text "For testing: Sets the log level (default: OFF, choices: TRACE, DEBUG, INFO, WARN, ERROR, OFF)"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, Options()) match {
    case Some(options) ⇒
      try {
        run(options)
      } catch {
        case e: Throwable ⇒
          e.printStackTrace()
          System.exit(1)
      }
    case None ⇒ System.exit(1)
  }

  // Report error and exit
  private def error(msg: String): Unit = {
    println(msg)
    System.exit(1)
  }

  // Uses the given Alarm Service Redis instance to act on the command line options
  private def run(options: Options): Unit = {
    options.logLevel.foreach(setLogLevel)

    val alarmService = Await.result(AlarmService(options.asName.getOrElse(AlarmService.defaultName)), timeout.duration)

    options.asConfig foreach (init(alarmService, _, options))
    options.severity.foreach(setSeverity(alarmService, _, options))
    if (options.refreshSeverity) refreshSeverity(alarmService, options)
    if (options.listAlarms) list(alarmService, options)
    if (options.monitorAlarms) monitorAlarms(alarmService, options)
    if (options.acknowledge) acknowledgeAlarm(alarmService, options)

    if (options.shutdown) {
      println(s"Shutting down the alarm service")
      alarmService.shutdown()
    }

    // Shutdown and exit
    if (!options.noExit && !options.monitorAlarms && !options.refreshSeverity) {
      system.terminate()
      System.exit(0)
    }
  }

  private def setLogLevel(level: String): Unit = {
    import ch.qos.logback.classic.Logger
    val l = Level.toLevel(level, Level.OFF)
    println(s"Setting log level to $level ($l)")
    LoggerFactory.getLogger("root").asInstanceOf[Logger].setLevel(l)
    LoggerFactory.getLogger("csw").asInstanceOf[Logger].setLevel(l)
  }

  // Handle the --init option
  private def init(alarmService: AlarmService, file: File, options: Options): Unit = {
    val problems = Await.result(alarmService.initAlarms(file, options.expire.get), timeout.duration)
    Problem.printProblems(problems)
    if (Problem.errorCount(problems) != 0) error(s"Failed to initialize Alarm Service with $file")
  }

  // Handle --severity option (set selected alarm severity)
  private def setSeverity(alarmService: AlarmService, sev: String, options: Options): Unit = {
    val severity = SeverityLevel(sev)
    if (severity.isEmpty) error(s"Invalid severity level: $sev")
    if (options.subsystem.isEmpty) error("Missing required --subsystem option")
    if (options.component.isEmpty) error("Missing required --component option")
    if (options.name.isEmpty) error("Missing required --name option (alarm name)")
    val key = AlarmKey(options.subsystem.get, options.component.get, options.name.get)
    Await.ready(alarmService.setSeverity(key, severity.get, options.expire.get), timeout.duration)
  }

  // Handle --refresh option (start an actor to continually refresh the selected alarm severity)
  private def refreshSeverity(alarmService: AlarmService, options: Options): Unit = {
    if (options.severity.isEmpty) error(s"Missing required --severity option")
    val severity = options.severity.flatMap(SeverityLevel(_))
    if (severity.isEmpty) error(s"Invalid severity level: $severity")
    if (options.subsystem.isEmpty) error("Missing required --subsystem option")
    if (options.component.isEmpty) error("Missing required --component option")
    if (options.name.isEmpty) error("Missing required --name option (alarm name)")

    val key = AlarmKey(options.subsystem.get, options.component.get, options.name.get)
    val map = Map(key → severity.get)
    system.actorOf(AlarmServiceSetSeverityActor.props(alarmService, map, options.expire.get))
  }

  // Handle the --list option
  private def list(alarmService: AlarmService, options: Options): Unit = {
    val alarms = Await.result(alarmService.getAlarms(AlarmKey(options.subsystem, options.component, options.name)), timeout.duration)
    alarms.foreach { alarm ⇒
      // XXX TODO: add format options?
      println(AlarmJson.toJson(alarm).prettyPrint)
    }
  }

  private def printAlarmStatus(alarmStatus: AlarmStatus): Unit = {
    val a = alarmStatus.alarm
    println(s"Alarm Status: ${a.subsystem}:${a.component}:${a.name}: ${alarmStatus.severity}")
  }

  // Handle the --monitor option
  private def monitorAlarms(alarmService: AlarmService, options: Options): Unit = {
    alarmService.monitorAlarms(AlarmKey(options.subsystem, options.component, options.name), None, Some(printAlarmStatus _))
  }

  // Handle the --acknowledge option
  private def acknowledgeAlarm(alarmService: AlarmService, options: Options): Unit = {
    Await.ready(
      alarmService.acknowledgeAlarm(AlarmKey(options.subsystem, options.component, options.name), options.expire.get),
      timeout.duration
    )
  }
}
