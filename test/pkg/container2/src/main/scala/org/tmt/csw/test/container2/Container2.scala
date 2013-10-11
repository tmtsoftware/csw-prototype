package org.tmt.csw.test.container2

import akka.actor.{Props, ActorSystem}
import akka.kernel.Bootable


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


