package org.tmt.csw.test.container2

import akka.actor._
import akka.kernel.Bootable
import akka.util.Timeout
import org.tmt.csw.pkg.Container
import akka.pattern.ask


// This class is started by the Akka microkernel in standalone mode
class Container2 extends Bootable {

  val system = ActorSystem("system")

  def startup(): Unit = {
    system.actorOf(Props[Container2Actor], "Container2Actor")
  }

  def shutdown(): Unit = {
    system.shutdown()
  }
}


/**
 * The main actor for this application.
 * This container holds the two HCDs.
 */
class Container2Actor extends Actor with ActorLogging {
  import scala.concurrent.duration._
  val duration: FiniteDuration = 2.seconds
  implicit val timeout = Timeout(duration)
  implicit val dispatcher = context.system.dispatcher

  def receive: Receive = {
    case x => log.info(s"Received unknown message: $x")
  }
  val container = Container.create("Container-2")

  val hcd2aProps = Hcd2.props("HCD-2A", "config.tmt.mobie.blue.filter")
  val hcd2bProps = Hcd2.props("HCD-2B", "config.tmt.mobie.blue.disperser")

  // For the Play Framework Demo
  val hcd2cProps = Hcd2.props("HCD-2C", "config.tmt.tel.base.pos")
  val hcd2dProps = Hcd2.props("HCD-2D", "config.tmt.tel.ao.pos.one")

  for {
    hcd2a <- (container ? Container.CreateComponent(hcd2aProps, "HCD-2A")).mapTo[ActorRef]
    hcd2b <- (container ? Container.CreateComponent(hcd2bProps, "HCD-2B")).mapTo[ActorRef]
    hcd2c <- (container ? Container.CreateComponent(hcd2cProps, "HCD-2C")).mapTo[ActorRef]
    hcd2d <- (container ? Container.CreateComponent(hcd2dProps, "HCD-2D")).mapTo[ActorRef]
  } {
    log.info(s"Created HCD-2A: $hcd2a")
    log.info(s"Created HCD-2B: $hcd2b")
    log.info(s"Created HCD-2C: $hcd2c")
    log.info(s"Created HCD-2D: $hcd2d")
  }
}
