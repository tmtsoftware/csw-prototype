package org.tmt.csw.test.container2

import akka.actor._
import org.tmt.csw.pkg.Container

object Container2 {
  // Container2 main
  def main(args: Array[String]): Unit = {
    akka.Main.main(Array(classOf[Container2Actor].getName))
  }
}

/**
 * The main actor for this application.
 * This container holds the two HCDs.
 */
class Container2Actor extends Actor with ActorLogging {

  def receive: Receive = {
    case actorRef: ActorRef => log.info(s"Created actor component: $actorRef")
    case x => log.error(s"Received unknown message: $x")
  }
  val container = Container.create("Container-2")

  val hcd2aProps = Hcd2.props("HCD-2A", "config.tmt.mobie.blue.filter")
  val hcd2bProps = Hcd2.props("HCD-2B", "config.tmt.mobie.blue.disperser")

  // For the Play Framework Demo
  val hcd2cProps = Hcd2.props("HCD-2C", "config.tmt.tel.base.pos")
  val hcd2dProps = Hcd2.props("HCD-2D", "config.tmt.tel.ao.pos.one")

  container ! Container.CreateComponent(hcd2aProps, "HCD-2A")
  container ! Container.CreateComponent(hcd2bProps, "HCD-2B")
  container ! Container.CreateComponent(hcd2cProps, "HCD-2C")
  container ! Container.CreateComponent(hcd2dProps, "HCD-2D")
}
