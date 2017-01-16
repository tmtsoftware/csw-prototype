package csw.examples.vslice

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.examples.vslice.assembly.TromboneAssembly
import csw.examples.vslice.hcd.TromboneHCD
import csw.services.alarms.{AlarmService, AlarmServiceAdmin}
import csw.services.cs.akka.ConfigServiceClient
import csw.services.events.{EventService, EventServiceAdmin}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Helper class for setting up the test environment
 */
object TestEnv {

  // For the tests, store the HCD's configuration in the config service (Normally, it would already be there)
  def createTromboneHcdConfig()(implicit system: ActorSystem): Unit = {
    val config = ConfigFactory.parseResources(TromboneHCD.resource.getPath)
    implicit val timeout = Timeout(5.seconds)
    Await.ready(ConfigServiceClient.saveConfigToConfigService(TromboneHCD.tromboneConfigFile, config), timeout.duration)
  }

  // For the tests, store the assembly's configuration in the config service (Normally, it would already be there)
  def createTromboneAssemblyConfig()(implicit system: ActorSystem): Unit = {
    createTromboneHcdConfig()
    implicit val timeout = Timeout(5.seconds)
    val config = ConfigFactory.parseResources(TromboneAssembly.resource.getPath)
    Await.ready(ConfigServiceClient.saveConfigToConfigService(TromboneAssembly.tromboneConfigFile, config), 5.seconds)
  }

  // Reset all redis based services before a test (assumes they are sharing the same Redis instance)
  def resetRedisServices()(implicit system: ActorSystem): Unit = {
    import system.dispatcher
    implicit val timeout = Timeout(10.seconds)
    // clear redis
    val eventService = Await.result(EventService(), timeout.duration)
    val eventServiceAdmin = EventServiceAdmin(eventService)
    Await.ready(eventServiceAdmin.reset(), timeout.duration)

    // initialize the list of alarms
    val alarmService = Await.result(AlarmService(), timeout.duration)
    val alarmServiceAdmin = AlarmServiceAdmin(alarmService)
    //$CSW_INSTALL/conf/alarms.conf
    val cswInstall = Option(System.getenv("CSW_INSTALL"))
    if (cswInstall.isEmpty) {
      println("Environment variable CSW_INSTALL is not set")
      System.exit(1)
    }
    Await.ready(alarmServiceAdmin.initAlarms(new File(s"${cswInstall.get}/conf/alarms.conf")), timeout.duration)
  }
}
