package csw.services.log

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

object TestLogging {
  def props(prefix: String): Props = Props(classOf[TestLogging], prefix)
}

case class TestLogging(override val prefix: String) extends Actor with PrefixedActorLogging {
  override def receive: Receive = {
    case x =>
      log.info(s"Received message: $x")
      assert(log.getMDC.get("prefix").toString == prefix)
  }
}

/**
 * Tests that the prefix for each actor is unique
 */
class PrefixedActorLoggingTests extends TestKit(ActorSystem("testsys"))
    with ImplicitSender with FunSuiteLike with BeforeAndAfterAll with LazyLogging {

  val a1 = system.actorOf(TestLogging.props("test1"))
  val a2 = system.actorOf(TestLogging.props("test2"))
  a1 ! "Test message one"
  a2 ! "Test message two"
  a1 ! "Test message three"
  a2 ! "Test message four"
}
