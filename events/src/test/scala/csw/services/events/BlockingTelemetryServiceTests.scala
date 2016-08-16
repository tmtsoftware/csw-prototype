package csw.services.events

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import scala.concurrent.duration._
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.util.config.Events.StatusEvent
import csw.util.config.StringKey
import org.scalatest.FunSuiteLike

/**
 * Created by abrighto on 04/08/16.
 */
class BlockingTelemetryServiceTests extends TestKit(ActorSystem("Test"))
    with ImplicitSender with FunSuiteLike with LazyLogging {

  test("Simple Test") {
    val kvsSettings = EventServiceSettings(system)
    val telemetryService = BlockingTelemetryService(5.seconds, TelemetryService(kvsSettings))
    val key = StringKey("testKey")
    val e1 = StatusEvent("test").add(key.set("Test Passed"))
    telemetryService.publish(e1)
    val e2Opt = telemetryService.get("test")
    assert(e2Opt.isDefined)
    assert(e1 == e2Opt.get)

    println(e2Opt.get(key))
    println("Redis shutdown completed")
  }
}
