package org.tmt.csw.test.app

import akka.actor.{ Actor, ActorSystem, Props }
import akka.kernel.Bootable
import akka.pattern.ask
import org.tmt.csw.cs.akka.{GetResult, GetRequest, ConfigServiceActor}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case object Start

class TestActor extends Actor {
  val configServiceActor = context.actorOf(Props(ConfigServiceActor()), name = "configService")

  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)

  def receive = {
    case Start => (configServiceActor ? GetRequest("some/test1/TestConfig1")).onSuccess {
      case GetResult(id) => println("XXX Get => " + id)
    }
  }
}

class TestApp extends Bootable {
  val system = ActorSystem("TestApp")

  def startup = {
    system.actorOf(Props[TestActor]) ! Start
  }

  def shutdown = {
    system.shutdown()
  }
}

