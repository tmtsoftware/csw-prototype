package csw.services.asconsole

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import csw.services.loc.LocationService
import ch.qos.logback.classic._
import csw.services.alarms.AlarmModel.{AlarmStatus, HealthStatus, SeverityLevel}
import csw.services.alarms._
import org.slf4j.LoggerFactory
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}
import csw.services.alarms.AscfValidation.Problem

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
    asName:           Option[String]  = None, // Alarm Service name
    asConfig:         Option[File]    = None, // Alarm Service Config File (ASCF)
    delete:           Boolean         = false,
    listAlarms:       Boolean         = false,
    shutdown:         Boolean         = false,
    subsystem:        Option[String]  = None,
    component:        Option[String]  = None,
    name:             Option[String]  = None, // Alarm name (with wildcards)
    severity:         Option[String]  = None,
    refreshSecs:      Option[Int]     = Some(5),
    refreshSeverity:  Boolean         = false,
    monitorAlarms:    Option[String]  = None,
    monitorHealth:    Option[String]  = None,
    monitorAll:       Boolean         = false,
    acknowledgeAlarm: Boolean         = false,
    resetAlarm:       Boolean         = false,
    shelved:          Option[Boolean] = None,
    activated:        Option[Boolean] = None,
    logLevel:         Option[String]  = Some("OFF"),
    noExit:           Boolean         = false
  )

  // XXX TODO: Add options for --list output format: pdf, html, json, config, text?

  // XXX TODO: Add option to set/display severity for an alarm

  // Parses the command line options
  private val parser = new scopt.OptionParser[Options]("asconsole") {
    head("asconsole", System.getProperty("CSW_VERSION"))

    opt[String]("as-name") valueName "<name>" action { (x, c) =>
      c.copy(asName = Some(x))
    } text "The name that was used to register the Alarm Service Redis instance (Default: 'Alarm Service')"

    opt[File]("init") valueName "<alarm-service-config-file>" action { (x, c) =>
      c.copy(asConfig = Some(x))
    } text "Initialize the set of available alarms from the given Alarm Service Config File (ASCF)"

    opt[Unit]("delete") action { (x, c) =>
      c.copy(delete = true)
    } text "When used with --init, deletes the existing alarm data before importing"

    opt[Unit]("list").action((_, c) =>
      c.copy(listAlarms = true)).text("Prints a list of all alarms (See other options to filter what is printed)")

    opt[Unit]("shutdown").action((_, c) =>
      c.copy(shutdown = true)).text("Shuts down the Alarm Service Redis instance")

    opt[String]("subsystem") valueName "<subsystem>" action { (x, c) =>
      c.copy(subsystem = Some(x))
    } text "Limits the selected alarms to those belonging to the given subsystem"

    opt[String]("component") valueName "<name>" action { (x, c) =>
      c.copy(component = Some(x))
    } text "Limits the selected alarms to those belonging to the given component (subsystem should also be specified)"

    opt[String]("name") valueName "<name>" action { (x, c) =>
      c.copy(name = Some(x))
    } text "Limits the selected alarms to those whose name matches the given value (may contain Redis wildcards)"

    opt[String]("severity") valueName "<severity>" action { (x, c) =>
      c.copy(severity = Some(x))
    } text "Sets the severity level for the alarm given by (--subsystem, --component, --name) to the given level (Alarm must be unique)"

    opt[String]("monitor-alarms") valueName "<shell-command>" action { (x, c) =>
      c.copy(monitorAlarms = Some(x))
    } text "Starts monitoring changes in the severity of alarm(s) given by (--subsystem, --component, --name) and calls the shell command with args: (subsystem, component, name, severity)"

    opt[String]("monitor-health") valueName "<shell-command>" action { (x, c) =>
      c.copy(monitorHealth = Some(x))
    } text "Starts monitoring the health of the subsystems or components given by (--subsystem, --component, --name) and calls the shell command with one arg: Good, Ill or Bad"

    opt[Unit]("monitor-all") action { (x, c) =>
      c.copy(monitorAll = true)
    } text "With this option all severity changes are reported, even if shelved or out of service"

    opt[Unit]("acknowledge") action { (x, c) =>
      c.copy(acknowledgeAlarm = true)
    } text "Acknowledge the alarm given by (--subsystem, --component, --name) (Alarm must be unique)"

    opt[Unit]("reset") action { (x, c) =>
      c.copy(resetAlarm = true)
    } text "Reset the latched state of the alarm given by (--subsystem, --component, --name) (Alarm must be unique)"

    opt[Boolean]("shelved") action { (x, c) =>
      c.copy(shelved = Some(x))
    } text "Set the shelved state of the alarm to true (shelved), or false (normal)"

    opt[Boolean]("activated") action { (x, c) =>
      c.copy(activated = Some(x))
    } text "Set the activated state of the alarm to true (activated), or false (normal)"

    opt[Unit]("refresh") action { (x, c) =>
      c.copy(refreshSeverity = true)
    } text "Continually refresh the given alarm's severity before it expires (use together with --subsystem, --component, --name, --severity)"

    opt[Int]("refresh-secs") action { (x, c) =>
      c.copy(refreshSecs = Some(x))
    } text "When --refresh was specified, the number of seconds between refreshes of the alarm's severity"

    opt[Unit]("no-exit") action { (x, c) =>
      c.copy(noExit = true)
    } text "For testing: prevents application from exiting the JVM"

    opt[String]("log") valueName "<log-level>" action { (x, c) =>
      c.copy(logLevel = Some(x))
    } text "For testing: Sets the log level (default: OFF, choices: TRACE, DEBUG, INFO, WARN, ERROR, OFF)"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, Options()) match {
    case Some(options) =>
      try {
        run(options)
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          System.exit(1)
      }
    case None => System.exit(1)
  }

  // Report error and exit
  private def error(msg: String): Unit = {
    println(msg)
    System.exit(1)
  }

  // Uses the given Alarm Service Redis instance to act on the command line options
  private def run(options: Options): Unit = {
    options.logLevel.foreach(setLogLevel)

    val alarmServiceName = options.asName.getOrElse(AlarmService.defaultName)
    val refreshSecs = options.refreshSecs.getOrElse(AlarmService.defaultRefreshSecs)
    val alarmService = Await.result(AlarmService(alarmServiceName, refreshSecs), timeout.duration)

    options.asConfig.foreach(init(alarmService, _, options))
    options.severity.foreach(setSeverity(alarmService, _, options))
    options.shelved.foreach(setShelved(alarmService, _, options))
    options.activated.foreach(setActivated(alarmService, _, options))
    if (options.refreshSeverity) refreshSeverity(alarmService, options)
    if (options.listAlarms) list(alarmService, options)
    if (options.monitorAlarms.isDefined || options.monitorHealth.isDefined) monitor(alarmService, options)
    if (options.acknowledgeAlarm) acknowledgeAlarm(alarmService, options)
    if (options.resetAlarm) resetAlarm(alarmService, options)

    if (options.shutdown) {
      println(s"Shutting down the alarm service")
      val admin = AlarmAdmin(alarmService)
      admin.shutdown()
    }

    // Shutdown and exit, unless an option was given that indicates keep running
    if (!options.noExit && options.monitorAlarms.isEmpty && options.monitorHealth.isEmpty && !options.refreshSeverity) {
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
    val admin = AlarmAdmin(alarmService)
    val problems = Await.result(admin.initAlarms(file, options.delete), timeout.duration)
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
    Await.ready(alarmService.setSeverity(key, severity.get), timeout.duration)
  }

  // Handle --shelved option (set selected alarm's shelved state)
  private def setShelved(alarmService: AlarmService, shelved: Boolean, options: Options): Unit = {
    val shelvedState = if (shelved) ShelvedState.Shelved else ShelvedState.Normal
    if (options.subsystem.isEmpty) error("Missing required --subsystem option")
    if (options.component.isEmpty) error("Missing required --component option")
    if (options.name.isEmpty) error("Missing required --name option (alarm name)")
    val key = AlarmKey(options.subsystem.get, options.component.get, options.name.get)
    Await.ready(alarmService.setShelvedState(key, shelvedState), timeout.duration)
  }

  // Handle --activated option (set selected alarm's activated state)
  private def setActivated(alarmService: AlarmService, activated: Boolean, options: Options): Unit = {
    val activatedState = if (activated) ActivationState.Normal else ActivationState.OutOfService
    if (options.subsystem.isEmpty) error("Missing required --subsystem option")
    if (options.component.isEmpty) error("Missing required --component option")
    if (options.name.isEmpty) error("Missing required --name option (alarm name)")
    val key = AlarmKey(options.subsystem.get, options.component.get, options.name.get)
    Await.ready(alarmService.setActivationState(key, activatedState), timeout.duration)
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
    val map = Map(key -> severity.get)
    system.actorOf(AlarmRefreshActor.props(alarmService, map))
  }

  // Handle the --list option
  private def list(alarmService: AlarmService, options: Options): Unit = {
    val alarms = Await.result(alarmService.getAlarms(AlarmKey(options.subsystem, options.component, options.name)), timeout.duration)
    alarms.foreach { alarm =>
      // XXX TODO: add format options?
      println(AlarmJson.toJson(alarm).prettyPrint)
    }
  }

  // XXX TODO: make option execute a shell command
  private def alarmStatusCallback(cmd: String)(alarmStatus: AlarmStatus): Unit = {
    import scala.sys.process._
    val a = alarmStatus.alarmKey
    s"$cmd ${a.subsystem} ${a.component} ${a.name} ${alarmStatus.currentSeverity.latched}".run()
  }

  // XXX TODO: make option execute a shell command
  private def healthStatusCallback(cmd: String)(healthStatus: HealthStatus): Unit = {
    import scala.sys.process._
    s"$cmd ${healthStatus.health}".run()
  }

  // Handle the --monitor* options
  private def monitor(alarmService: AlarmService, options: Options): Unit = {
    alarmService.monitorHealth(
      AlarmKey(options.subsystem, options.component, options.name),
      None,
      options.monitorAlarms.map(alarmStatusCallback),
      options.monitorHealth.map(healthStatusCallback),
      options.monitorAll
    )
  }

  // Handle the --acknowledge option
  private def acknowledgeAlarm(alarmService: AlarmService, options: Options): Unit = {
    Await.ready(
      alarmService.acknowledgeAlarm(AlarmKey(options.subsystem, options.component, options.name)),
      timeout.duration
    )
  }

  // Handle the --reset option
  private def resetAlarm(alarmService: AlarmService, options: Options): Unit = {
    Await.ready(
      alarmService.resetAlarm(AlarmKey(options.subsystem, options.component, options.name)),
      timeout.duration
    )
  }
}

