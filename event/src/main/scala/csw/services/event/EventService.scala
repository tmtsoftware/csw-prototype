package csw.services.event

import csw.util.cfg.ConfigSerializer._
import org.hornetq.api.core.TransportConfiguration
import org.hornetq.api.core.client._
import org.hornetq.core.remoting.impl.invm.{InVMConnectorFactory, InVMAcceptorFactory}
import org.hornetq.core.remoting.impl.netty.{NettyAcceptorFactory, NettyConnectorFactory}
import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
 * An event service based on HornetQ
 * (XXX TODO: Change implementation, since HornetQ no longer supported)
 */
object EventService {

  /**
   * Describes an event service connection
   * @param serverLocator locates a server
   * @param sf entry point to create and configure HornetQ resources to produce and consume messages
   * @param session single-thread object required for producing and consuming messages
   */
  case class EventServiceInfo(
      serverLocator: ServerLocator,
      sf:            ClientSessionFactory,
      session:       ClientSession
  ) {

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
   * Connects to a HornetQ server using the given settings.
   */
  private[event] def connectToServer(settings: EventServiceSettings): EventServiceInfo =
    connectToHornetQ(
      settings.eventServiceHostname.getOrElse("127.0.0.1"),
      settings.eventServicePort.getOrElse(5445),
      settings.useEmbeddedHornetq
    )

  /**
   * Connects to a HornetQ server.
   * If useEmbeddedHornetq is set to true in the settings (reference.conf), connect to
   * an embedded (in this jvm) server, otherwise use the host and port settings to connect to
   * an external Hornetq server.
   */
  private[event] def connectToHornetQ(host: String, port: Int, useEmbedded: Boolean): EventServiceInfo = {
    val serverLocator = if (useEmbedded) {
      HornetQClient.createServerLocatorWithoutHA(
        new TransportConfiguration(classOf[InVMConnectorFactory].getName)
      )
    } else {
      val map = Map("host" → host, "port" → port)
      HornetQClient.createServerLocatorWithoutHA(
        new TransportConfiguration(
          classOf[NettyConnectorFactory].getName,
          map.asJava.asInstanceOf[java.util.Map[String, Object]]
        )
      )
    }

    // Prevents blocking when queue is full, but requires consumers to consume quickly
    //    serverLocator.setProducerWindowSize(-1)
    //    serverLocator.setConsumerWindowSize(-1)

    val sf = serverLocator.createSessionFactory
    val session = sf.createSession
    session.start()
    EventServiceInfo(serverLocator, sf, session)
  }

  /**
   * Starts an embedded Hornetq server
   * See http://docs.jboss.org/hornetq/2.4.0.beta1/docs/user-manual/html_single/#d0e13503
   */
  def startEmbeddedHornetQ(): Unit = {
    import org.hornetq.core.config.impl.ConfigurationImpl
    import org.hornetq.core.server.embedded.EmbeddedHornetQ

    val config = new ConfigurationImpl()
    config.setPersistenceEnabled(false)
    config.setSecurityEnabled(false)

    // Use paging to avoid out of memory errors in Hornetq (configure this?)
    //    val addressSettings = config.getAddressesSettings.get("#")
    //    addressSettings.setMaxSizeBytes(104857600)
    //    addressSettings.setPageSizeBytes(10485760)
    //    addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE)

    val transports = new java.util.HashSet[TransportConfiguration]()
    transports.add(new TransportConfiguration(classOf[NettyAcceptorFactory].getName))
    transports.add(new TransportConfiguration(classOf[InVMAcceptorFactory].getName))
    config.setAcceptorConfigurations(transports)
    val server = new EmbeddedHornetQ()
    server.setConfiguration(config)
    server.start()
  }

  /**
   * Initialize from the settings in resources.conf or application.conf
   * @param prefix the prefix for the events that will be published
   * @param settings the settings for connecting to the server
   */
  def apply(prefix: String, settings: EventServiceSettings): EventService =
    EventService(prefix, settings.eventServiceHostname.getOrElse("127.0.0.1"),
      settings.eventServicePort.getOrElse(5445),
      settings.useEmbeddedHornetq)
}

/**
 * Represents connection to the event service server (currently Hornetq)
 *
 * @param prefix the prefix for the events that will be published
 * @param host the server host (default: localhost: 127.0.0.1)
 * @param port the server port (default: 5445)
 * @param useEmbedded if true (default) start the server, otherwise look for one already running
 */
case class EventService(prefix: String, host: String = "127.0.0.1", port: Int = 5445, useEmbedded: Boolean = false) {

  import EventService._

  private val hq = connectToHornetQ(host, port, useEmbedded)
  private val producer = hq.session.createProducer(prefix)

  /**
   * Publishes the given event (channel is the event prefix).
   * @param event the event to publish
   * @param expire time to live for event
   */
  def publish(event: Event, expire: FiniteDuration = 1.second): Unit = {
    val message = hq.session.createMessage(false)
    val buf = message.getBodyBuffer
    buf.clear()
    buf.writeBytes(write(event))
    message.setExpiration(System.currentTimeMillis() + expire.toMillis)
    producer.send(message)
  }

  /**
   * Closes the connection to the Hornetq server
   */
  def close(): Unit = hq.close()
}
