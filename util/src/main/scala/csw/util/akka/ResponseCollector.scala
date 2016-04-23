package csw.util.akka

import akka.actor._
import akka.util.Timeout

import scala.concurrent.Promise
import scala.concurrent.duration._

// See http://www.cakesolutions.net/teamblogs/reusable-response-collectors-in-akka-0

trait ResponseTracker[T] {
  def addResponse(response: T): ResponseTracker[T]

  def isDone: Boolean
}

sealed trait ResultState

case object Full extends ResultState

case object Partial extends ResultState

case class Result[T](values: Iterable[T], state: ResultState)

class ResponseCollector[T](
  timeout:        FiniteDuration,
  initialTracker: ResponseTracker[T],
  result:         Promise[Result[T]],
  matcher:        PartialFunction[Any, T]
)
    extends Actor with ActorLogging {

  import context.dispatcher

  private val scheduledTimeout = context.system.scheduler.scheduleOnce(
    timeout, self, ReceiveTimeout
  )

  private def ready(responses: Vector[T], tracker: ResponseTracker[T]): Receive = {
    case m if matcher.isDefinedAt(m) ⇒
      val response = matcher(m)
      val nextResponses = responses :+ response
      val nextTracker = tracker.addResponse(response)

      if (nextTracker.isDone) {
        log.info("All responses received.")
        result.success(Result(nextResponses, Full))
        scheduledTimeout.cancel()
        context.stop(self)
      } else {
        context.become(ready(nextResponses, nextTracker))
      }

    case ReceiveTimeout ⇒
      log.warning("Response collecting timed out")
      result.success(Result(responses, Partial))
      context.stop(self)

    case m ⇒
      log.warning("Unknown message: {}", m)
  }

  def receive: Receive = ready(Vector.empty, initialTracker)
}

object ResponseCollector {
  def props[T](
    tracker: ResponseTracker[T],
    result:  Promise[Result[T]],
    matcher: PartialFunction[Any, T]
  )(implicit timeout: Timeout): Props =
    Props(new ResponseCollector(timeout.duration, tracker, result, matcher))

  def apply[T](tracker: ResponseTracker[T], matcher: PartialFunction[Any, T])(implicit timeout: Timeout, factory: ActorRefFactory) = {
    val result = Promise[Result[T]]()
    val ref = factory.actorOf(props(tracker, result, matcher))
    (result.future, ref)
  }
}

object Countdown {
  def apply(expectedMessagesCount: Int): Countdown = new Countdown(expectedMessagesCount)
}

class Countdown(expectedMessagesCount: Int) extends ResponseTracker[Any] {
  require(expectedMessagesCount >= 0)

  def isDone: Boolean = expectedMessagesCount == 0

  def addResponse(response: Any): Countdown =
    new Countdown((expectedMessagesCount - 1) max 0)
}

object MatchIds {
  def apply[Msg, Id](expectedIds: Set[Id], toId: Msg ⇒ Id): MatchIds[Msg, Id] =
    new MatchIds(expectedIds, toId)
}

class MatchIds[Msg, Id](expectedIds: Set[Id], toId: Msg ⇒ Id)
    extends ResponseTracker[Msg] {

  def isDone: Boolean = expectedIds.isEmpty

  def addResponse(response: Msg): MatchIds[Msg, Id] =
    new MatchIds(expectedIds - toId(response), toId)
}

case class Data(id: String, contents: String) {
  override def toString: String = s"[$id: $contents]"
}

object DataStore {
  def props(id: String, items: Map[Int, String]) = Props(new DataStore(id, items))
}

class DataStore(id: String, items: Map[Int, String]) extends Actor {
  def receive: Receive = {
    case i: Int ⇒ items.get(i).foreach(v ⇒ sender() ! Data(id, v))
  }
}

object Main extends App {
  implicit val system = ActorSystem()

  // Stores
  val allStores = List(
    system.actorOf(DataStore.props(
      "name",
      Map(1 → "Mike", 2 → "Robert", 3 → "Joe")
    )),
    system.actorOf(DataStore.props(
      "location",
      Map(1 → "UK", 2 → "Sweden", 3 → "Germany")
    )),
    system.actorOf(DataStore.props(
      "lastPurchase",
      Map(1 → "couch", 2 → "laptop")
    ))
  )

  // Set up response collectors
  val matcher: PartialFunction[Any, Data] = {
    case d: Data ⇒ d
  }

  def getId(e: Data): String = e.id

  val tracker = MatchIds(Set("name", "location", "lastPurchase"), getId)

  implicit val timeout = Timeout(1.second)
  val (result1, collector1) = ResponseCollector(tracker, matcher)
  val (result2, collector2) = ResponseCollector(tracker, matcher)
  val (result3, collector3) = ResponseCollector(tracker, matcher)

  // Fetch data
  allStores.foreach { store ⇒
    store.tell(1, collector1)
    store.tell(2, collector2)
    store.tell(3, collector3)
  }

  // Print results
  def printResult(r: Result[Data]): Unit = {
    val status = r.state match {
      case Full    ⇒ "All values received"
      case Partial ⇒ "Only some values received"
    }
    val values = r.values.mkString(", ")
    println(s"$status: $values")
  }

  implicit val ec = system.dispatcher

  result1.foreach(printResult)
  result2.foreach(printResult)
  result3.foreach(printResult)

  Thread.sleep(2000)
  system.terminate()
}