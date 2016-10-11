package csw.examples.vslice.assembly

/**
  * TMT Source Code: 10/10/16.
  */
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.LocationService
import org.scalatest.{BeforeAndAfterAll, _}

import scala.concurrent.duration._

object TromboneAssemblyCompTests {
  LocationService.initInterface()

  val system = ActorSystem("TromboneAssemblyCompTests")
}
class TromboneAssemblyCompTests extends TestKit(TromboneAssemblyCompTests.system) with ImplicitSender
  with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {


}
