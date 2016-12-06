package csw.examples.vslice

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.examples.vslice.assembly.TromboneAssembly
import csw.examples.vslice.hcd.TromboneHCD
import csw.services.cs.akka.ConfigServiceClient

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
}
