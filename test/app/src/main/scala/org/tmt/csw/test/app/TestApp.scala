package org.tmt.csw.test.app

import akka.actor.{ActorSystem, Actor, Props, Terminated}
import akka.kernel.Bootable

case object Start


// This class is started by the Akka microkernel in standalone mode
class TestApp extends Bootable {

  def startup() {
    ActorFactory.system.actorOf(Props[TestActor]) ! Start
  }

  def shutdown() {
    ActorFactory.system.shutdown()
  }
}


