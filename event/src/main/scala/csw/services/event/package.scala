package csw.services

import org.hornetq.api.core.client.{ServerLocator, ClientSessionFactory, HornetQClient, ClientSession}
import org.hornetq.api.core.TransportConfiguration
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory
import scala.collection.JavaConverters._
import akka.actor.ActorSystem
import csw.util.Configuration

//import scala.collection.JavaConversions._

package object event {

  /**
   * An Event here is just a [[Configuration]]
   */
  type Event = Configuration

  case class HornetqInfo(settings: EventServiceSettings,
                         serverLocator: ServerLocator,
                         sf: ClientSessionFactory,
                         session: ClientSession) {

    def close(): Unit = {
      session.close()
      sf.close()
      serverLocator.close()
    }
  }

  /**
   * Connects to the HornetQ server
   */
  private[event] def connectToHornetQ(actorSystem: ActorSystem): HornetqInfo = {
    val settings = EventServiceSettings(actorSystem)
    val map = Map("host" -> settings.eventServiceHostname, "port" -> settings.eventServicePort)
    val serverLocator = HornetQClient.createServerLocatorWithoutHA(
      new TransportConfiguration(classOf[NettyConnectorFactory].getName,
        map.asJava.asInstanceOf[java.util.Map[String, Object]]))

    // Prevents blocking when queue is full, but requires consumers to consume quickly
    serverLocator.setProducerWindowSize(-1)
    serverLocator.setConsumerWindowSize(-1)

    val sf = serverLocator.createSessionFactory
    val session = sf.createSession
    session.start()
    HornetqInfo(settings, serverLocator, sf, session)
  }
}

