//package csw.examples.vsliceJava.assembly
//
///**
// * TMT Source Code: 10/10/16.
// */
//import akka.actor.ActorSystem
//import akka.testkit.{ImplicitSender, TestKit}
//import com.typesafe.scalalogging.slf4j.LazyLogging
//import csw.services.loc.LocationService
//
//object TromboneAssemblyCompTests {
//  LocationService.initInterface()
//
//  val system = ActorSystem("TromboneAssemblyCompTests")
//}
//
//class TromboneAssemblyCompTests extends TestKit(TromboneAssemblyCompTests.system) with ImplicitSender
//    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {
//
//}
