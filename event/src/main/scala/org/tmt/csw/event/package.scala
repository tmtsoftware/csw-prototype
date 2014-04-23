package org.tmt.csw

import org.tmt.csw.util.Configuration
import org.hornetq.api.core.client.{ClientSessionFactory, HornetQClient, ClientSession}
import org.hornetq.api.core.TransportConfiguration
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory
import scala.collection.JavaConverters._
import akka.actor.ActorSystem

//import scala.collection.JavaConversions._

package object event {

  /**
   * An Event here is just a [[Configuration]]
   */
  type Event = Configuration

  /**
   * Connects to the HornetQ server
   */
  private[event] def connectToHornetQ(actorSystem: ActorSystem): (ClientSessionFactory, ClientSession) = {
    val settings = EventServiceSettings(actorSystem)
    val map = Map("host" -> settings.eventServiceHostname, "port" -> settings.eventServicePort)
    val serverLocator = HornetQClient.createServerLocatorWithoutHA(
      new TransportConfiguration(classOf[NettyConnectorFactory].getName,
        map.asJava.asInstanceOf[java.util.Map[String,Object]]))

    // Prevents blocking when queue is full, but requires consumers to consume quickly
    serverLocator.setProducerWindowSize(-1)
    serverLocator.setConsumerWindowSize(-1)

    val sf = serverLocator.createSessionFactory
    val session = sf.createSession
    session.start()
    (sf, session)
  }
}

