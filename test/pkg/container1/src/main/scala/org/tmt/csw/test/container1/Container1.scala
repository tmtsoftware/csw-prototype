package org.tmt.csw.test.container1

import akka.actor._
import akka.kernel.Bootable

/**
 * A test that runs each of the classes below in a separate JVM (See the sbt-multi-jvm plugin)
 */


// This class is started by the Akka microkernel in standalone mode
class Container1 extends Bootable {

  val system = ActorSystem("Container-1")

  def startup(): Unit = {
    system.actorOf(Props[Container1Actor], "Container1Actor")
  }

  def shutdown(): Unit = {
    system.shutdown()
  }
}


