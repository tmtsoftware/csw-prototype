package org.tmt.csw.test.client

import akka.actor.{Props, ActorSystem}
import akka.kernel.Bootable


// This class is started by the Akka microkernel in standalone mode
class TestClient extends Bootable {

  val system = ActorSystem("TestClient")

  def startup() {
    system.actorOf(Props[TestClientActor])
  }

  def shutdown() {
    system.shutdown()
  }
}


