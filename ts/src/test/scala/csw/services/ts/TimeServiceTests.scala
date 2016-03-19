package csw.services.ts

import java.time._

import akka.actor._
import akka.testkit.{ TestKit, ImplicitSender }
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.scalatest.{ BeforeAndAfterAll, FunSuiteLike }

/**
 * Tests the TimeService
 */
class TimeServiceTests extends TestKit(ActorSystem("Test")) with ImplicitSender with FunSuiteLike
    with LazyLogging with BeforeAndAfterAll {

  test("Basic Java Time Tests") {
    import TimeService._

    // Assume an eastern time zone for tests
    val eclock = Clock.system(ZoneId.of("America/New_York"))

    // Can't do much test to see now equal implying bad clocks
    val nyNow = LocalTime.now(eclock)
    val hwNow = hawaiiLocalTimeNow
    val now = localTimeNow

    assert(nyNow.isAfter(hwNow))
    // Try to determine that we have Hawaii time
    assert(!hwNow.equals(now)) // Not the same, good

    val utcNow = UTCTimeNow
    val taiNow = TAITimeNow

    assert(taiNow.isAfter(utcNow))
  }

  test("Call scheduler once") {
    import scala.concurrent.duration._

    val timerTest = system.actorOf(Props(new TestScheduler("tester", self)))
    timerTest ! "once"

    within(10.seconds) {
      expectMsg("done")
    }

  }

  test("Call scheduler (with 5 counts in 5 seconds)") {
    import scala.concurrent.duration._

    val timerTest = system.actorOf(Props(new TestScheduler("tester", self)))
    timerTest ! "five"

    within(10.seconds) {
      val cancellable = expectMsgType[Cancellable]
      logger.info(s"Received cancellable: $cancellable")
      val count = expectMsgType[Int]
      logger.info(s"Executed $count scheduled messages")
      assert(count == 5)
      cancellable.cancel()
    }

  }

  case class TestScheduler(name: String, caller: ActorRef) extends Actor with ActorLogging with TimeService.TimeServiceScheduler {

    import TimeService._

    var count = 0

    def receive: Receive = {
      case "once" ⇒
        log.info("Received once start")
        scheduleOnce(localTimeNow.plusSeconds(5), context.self, "once-done")
      case "five" ⇒
        log.info("Received multi start")
        val c = schedule(localTimeNow.plusSeconds(1), java.time.Duration.ofSeconds(1), context.self, "count")
        caller ! c //Return the cancellable
      case "count" ⇒
        count = count + 1
        log.info(s"Count: $count")
        if (count >= 5) caller ! count
      case "once-done" ⇒
        log.info("Received Done")
        caller ! "done"
    }
  }

}
