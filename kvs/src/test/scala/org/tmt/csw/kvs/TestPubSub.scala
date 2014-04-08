package org.tmt.csw.kvs

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}
import com.typesafe.scalalogging.slf4j.Logging
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import redis.ByteStringFormatter

class TestPubSub extends TestKit(ActorSystem("Test"))
  with ImplicitSender with FunSuiteLike with Logging with BeforeAndAfterAll {

  val numSecs = 30 // number of seconds to run
  val subscriber = system.actorOf(Props(classOf[Subscriber]))
  val publisher = system.actorOf(Props(classOf[Publisher], self, numSecs))

  test("Test subscriber") {
    within(numSecs+2 seconds) {
      expectMsg("done")
      subscriber ! "done"
      val count = expectMsgType[Int]
      val msgPerSec = count/numSecs
      logger.info(s"Recieved $count events in $numSecs seconds ($msgPerSec per second)")
    }
  }

  override def afterAll(): Unit = {
    system.shutdown()
  }
}

// A test class that publishes events
private case class Publisher(caller: ActorRef, numSecs: Int) extends Actor with ActorLogging with EventPublisher {
  implicit val execContext: ExecutionContext = context.dispatcher
  val root = "tmt.mobie.red.dat.exposureInfo"
  val expTime = 100 // ms
  var nextId = 0
  var done = false

  context.system.scheduler.scheduleOnce(numSecs seconds) {
    caller ! "done"
    done = true
  }

  // Schedule events (Note: Akka scheduler has resolution of about 100ms,
  // so if we want to test faster, we need to use a different kind of timer)
//  context.system.scheduler.schedule(0 millisecond, expTime millisecond)(publish(root, nextEvent()))
  val formatter = implicitly[ByteStringFormatter[Event]] // XXX temp
  val ev = nextEvent() // XXX temp
  val bs = formatter.serialize(ev)  // XXX temp
  while(!done) {
//    publish(root, nextEvent())
//    publish(root, ev)
    tmpPublish(root, bs)
    Thread.`yield`()
  }

  def nextEvent(): Event = {
    val time = System.currentTimeMillis()
    nextId = nextId + 1
    Event()
      .withValue(s"$root.eventId", nextId)
      .withValue(s"$root.exposureTime.value", expTime)
      .withValue(s"$root.exposureTime.units", "milliseconds")
      .withValue(s"$root.startTime", time - expTime)
      .withValue(s"$root.endTime", time)
  }

  override def receive: Receive = {
    case x => log.error(s"Unexpected message $x")
  }
}

// A test class that subscribes to events
private class Subscriber extends Actor with ActorLogging with EventSubscriber {
  implicit val execContext: ExecutionContext = context.dispatcher
  implicit val actorSytem = context.system
  var count = 0
  val kvs: KeyValueStore = RedisKeyValueStore()
  val root = "tmt.mobie.red.dat.exposureInfo"
  val idKey = s"$root.eventId"

  subscribe("tmt.mobie.red.dat.*")

  override def receive: Receive = {
    case event: Event =>
      count = count + 1
      if (count % 10000 == 0)
        log.info(s"Received $count events so far")
//      if (count % 100 == 0)
//        println(event)
      // kvs.get(idKey)

    case "done" => sender ! count
    case x => log.error(s"Unexpected message $x")
  }
}

