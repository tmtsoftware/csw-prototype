package csw.services.pkg

import akka.actor.Props
import csw.services.ccs.PeriodicController
import csw.services.loc.{ServiceType, ServiceId}

import scala.concurrent.duration._

case class TestHcd(name: String) extends Hcd
  with PeriodicController with LifecycleHandler {

  override def receive: Receive = receiveCommands orElse receiveLifecycleCommands

  override def rate: FiniteDuration = 1.second

  override def process(): Unit = {
    nextConfig match {
      case Some(config) =>
    }
  }
}
