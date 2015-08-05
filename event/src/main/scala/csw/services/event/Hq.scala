package csw.services.event

import akka.actor.{ ActorSystem, Actor }
import org.hornetq.api.core.TransportConfiguration
import org.hornetq.api.core.client.{ HornetQClient, ClientSession, ClientSessionFactory, ServerLocator }
import org.hornetq.core.remoting.impl.invm.{ InVMConnectorFactory, InVMAcceptorFactory }
import org.hornetq.core.remoting.impl.netty.{ NettyAcceptorFactory, NettyConnectorFactory }
import scala.collection.JavaConverters._

object Hq {

  /**
   * Describes a Hornetq connection
   * @param settings the settings (taked from reference.conf by default)
   * @param serverLocator locates a server
   * @param sf entry point to create and configure HornetQ resources to produce and consume messages
   * @param session single-thread object required for producing and consuming messages
   */
  case class HornetqInfo(settings: EventServiceSettings,
                         serverLocator: ServerLocator,
                         sf: ClientSessionFactory,
                         session: ClientSession) {

    /**
     * Closes the hornetq connection and session
     */
    def close(): Unit = {
      session.close()
      sf.close()
      serverLocator.close()
    }
  }

  /**
   * Connects to a HornetQ server.
   * If useEmbeddedHornetq is set to true in the settings (reference.conf), connect to
   * an embedded (in this jvm) server, otherwise use the host and port settings to connect to
   * an external Hornetq server.
   */
  private[event] def connectToHornetQ(actorSystem: ActorSystem): HornetqInfo = {
    val settings = EventServiceSettings(actorSystem)

    val serverLocator = if (settings.useEmbeddedHornetq) {
      HornetQClient.createServerLocatorWithoutHA(
        new TransportConfiguration(classOf[InVMConnectorFactory].getName))
    } else {
      val host = settings.eventServiceHostname.getOrElse("127.0.0.1")
      val port = settings.eventServicePort.getOrElse(5445)
      val map = Map("host" -> host, "port" -> port)
      HornetQClient.createServerLocatorWithoutHA(
        new TransportConfiguration(classOf[NettyConnectorFactory].getName,
          map.asJava.asInstanceOf[java.util.Map[String, Object]]))
    }

    // Prevents blocking when queue is full, but requires consumers to consume quickly
    serverLocator.setProducerWindowSize(-1)
    serverLocator.setConsumerWindowSize(-1)

    val sf = serverLocator.createSessionFactory
    val session = sf.createSession
    session.start()
    HornetqInfo(settings, serverLocator, sf, session)
  }

  /**
   * Starts an embedded Hornetq server
   * See http://docs.jboss.org/hornetq/2.4.0.beta1/docs/user-manual/html_single/#d0e13503
   */
  def startEmbeddedHornetQ(): Unit = {
    import org.hornetq.core.config.impl.ConfigurationImpl
    import org.hornetq.core.server.embedded.EmbeddedHornetQ

    val config = new ConfigurationImpl()
    config.setPersistenceEnabled(false);
    config.setSecurityEnabled(false);
    val transports = new java.util.HashSet[TransportConfiguration]()
    transports.add(new TransportConfiguration(classOf[NettyAcceptorFactory].getName))
    transports.add(new TransportConfiguration(classOf[InVMAcceptorFactory].getName))
    config.setAcceptorConfigurations(transports)
    val server = new EmbeddedHornetQ()
    server.setConfiguration(config)
    server.start()
    //    server.stop()
  }
}

import Hq._

/**
 * Represents connection to Hornetq
 */
trait Hq {
  this: Actor ⇒
  private[event] val hq = connectToHornetQ(context.system)
}
