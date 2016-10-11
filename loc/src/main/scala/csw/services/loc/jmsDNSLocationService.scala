package csw.services.loc

import java.net.{Inet6Address, InetAddress, NetworkInterface, URI}
import javax.jmdns.{JmDNS, ServiceInfo}

import akka.actor.{ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionKey}
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.loc.Connection.{AkkaConnection, HttpConnection}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.Future

/**
 * TMT Source Code: 8/2/16.
 */
object jmsDNSLocationService extends LocationServiceProvider {

  import LocationServiceProvider._

  private val logger = Logger(LoggerFactory.getLogger(jmsDNSLocationService.getClass))

  // Share the JmDNS instance within this jvm for better performance
  // (Note: Using lazy initialization, since this should run after calling initInterface() below
  private lazy val registry = getRegistry

  // Used to log a warning if initInterface was not called before registering
  private var initialized = false

  def trackerProps(replyTo: Option[ActorRef]) = jmsDNSLocationTracker.props(getRegistry, replyTo)

  /**
   * Sets the "akka.remote.artery.canonical.hostname" and net.mdns.interface system properties, if not already
   * set on the command line (with -D), so that any services or akka actors created will use and publish the correct IP address.
   * This method should be called before creating any actors or web services that depend on the location service.
   *
   * Note that calling this method overrides any setting for akka.remote.artery.canonical.hostname in the akka config file.
   * Since the application config is immutable and cached once it is loaded, I can't think of a way to take the config
   * setting into account here. This should not be a problem, since we don't want to hard code host names anyway.
   */
  def initInterface(): Unit = {
    if (!initialized) {
      initialized = true
      case class Addr(index: Int, addr: InetAddress)
      def defaultAddr = Addr(0, InetAddress.getLocalHost)

      def filter(a: Addr): Boolean = {
        // Don't use ipv6 addresses yet, since it seems to not be working with the current akka version
        !a.addr.isLoopbackAddress && !a.addr.isInstanceOf[Inet6Address]
      }
      // Get this host's primary IP address.
      // Note: The trick to getting the right one seems to be in sorting by network interface index
      // and then ignoring the loopback address.
      // I'm assuming that the addresses are sorted by network interface priority (which seems to be the case),
      // although this is not documented anywhere.
      def getIpAddress: String = {
        import scala.collection.JavaConversions._
        val addresses = for {
          i <- NetworkInterface.getNetworkInterfaces
          a <- i.getInetAddresses
        } yield Addr(i.getIndex, a)
        addresses.toList.sortWith(_.index < _.index).find(filter).getOrElse(defaultAddr).addr.getHostAddress
      }

      val akkaKey = "akka.remote.artery.canonical.hostname"
      val mdnsKey = "net.mdns.interface"
      //    val config = ConfigFactory.load()
      val mdnsHost = Option(System.getProperty(mdnsKey))
      mdnsHost.foreach(h => logger.debug(s"Found system property for $mdnsKey: $h"))
      //    val akkaHost = if (config.hasPath(akkaKey) && config.getString(akkaKey).nonEmpty) Some(config.getString(akkaKey)) else None
      val akkaHost = Option(System.getProperty(akkaKey))
      akkaHost.foreach(h => logger.debug(s"Found system property for: $akkaKey: $h"))
      val host = akkaHost.getOrElse(mdnsHost.getOrElse(getIpAddress))
      logger.debug(s"Using $host as listening IP address")
      System.setProperty(akkaKey, host)
      System.setProperty(mdnsKey, host)
    }
  }

  // Get JmDNS instance
  private def getRegistry: JmDNS = {
    if (!initialized) logger.warn("LocationService.initInterface() should be called once before using this class or starting any actors!")
    val hostname = Option(System.getProperty("akka.remote.artery.canonical.hostname"))
    val registry = if (hostname.isDefined) {
      val addr = InetAddress.getByName(hostname.get)
      JmDNS.create(addr, hostname.get)
    } else {
      JmDNS.create()
    }
    logger.debug(s"Using host = ${registry.getHostName} (${registry.getInetAddress})")
    sys.addShutdownHook(registry.close())
    registry
  }

  // Multicast DNS service type
  private val dnsType = "_csw._tcp.local."

  // -- Keys used to store values in DNS records --

  // URI path part
  private val PATH_KEY = "path"

  // Akka system name
  private val SYSTEM_KEY = "system"

  // Indicates the part of a command service config that this service is interested in
  private val PREFIX_KEY = "prefix"

  private case class JMSRegisterResult(registry: JmDNS, info: ServiceInfo, componentId: ComponentId) extends RegistrationResult {
    override def unregister(): Unit = registry.unregisterService(info)
  }

  /**
   * Registers the given service for the local host and the given port
   * (The full name of the local host will be used)
   *
   * @param componentId describes the component or service
   * @param actorRef    the actor reference for the actor being registered
   * @param prefix      indicates the part of a command service config that this service is interested in
   */

  def registerAkkaConnection[JMSRegisteryResult](componentId: ComponentId, actorRef: ActorRef, prefix: String = "")(implicit system: ActorSystem): Future[RegistrationResult] = {
    import system.dispatcher
    val connection = AkkaConnection(componentId)
    Future {
      val uri = getActorUri(actorRef, system)
      val values = Map(
        PATH_KEY -> uri.getPath,
        SYSTEM_KEY -> uri.getUserInfo,
        PREFIX_KEY -> prefix
      )
      val service = ServiceInfo.create(dnsType, connection.toString, uri.getPort, 0, 0, values.asJava)
      registry.registerService(service)
      logger.debug(s"Registered Akka $connection at $uri with $values")
      JMSRegisterResult(registry, service, componentId)
    }
  }

  /**
   * Registers the given service for the local host and the given port
   * (The full name of the local host will be used)
   *
   * @param componentId describes the component or service
   * @param port        the port the service is running on
   * @param path        the path part of the URI (default: empty)
   * @return an object that can be used to close the connection and unregister the service
   */
  def registerHttpConnection[JMSRegistryResult](componentId: ComponentId, port: Int, path: String = "")(implicit system: ActorSystem): Future[RegistrationResult] = {
    import system.dispatcher
    val connection = HttpConnection(componentId)
    Future {
      val values = Map(
        PATH_KEY -> path
      )
      val service = ServiceInfo.create(dnsType, connection.toString, port, 0, 0, values.asJava)
      registry.registerService(service)
      logger.debug(s"Registered HTTP $connection")
      JMSRegisterResult(registry, service, componentId)
    }
  }

  /**
   * Unregisters the connection from the location service
   * (Note: it can take some time before the service is removed from the list: see
   * comments in registry.unregisterService())
   */
  def unregisterConnection(connection: Connection): Unit = {
    import scala.collection.JavaConverters._
    logger.debug(s"Unregistered connection: $connection")
    val si = ServiceInfo.create(dnsType, connection.toString, 0, 0, 0, Map.empty[String, String].asJava)
    registry.unregisterService(si)
  }

  // --- Used to get the full path URI of an actor from the actorRef ---
  private class RemoteAddressExtensionImpl(system: ExtendedActorSystem) extends Extension {
    def address = system.provider.getDefaultAddress
  }

  private object RemoteAddressExtension extends ExtensionKey[RemoteAddressExtensionImpl]

  // Gets the full URI for the actor
  private def getActorUri(actorRef: ActorRef, system: ActorSystem): URI =
    new URI(actorRef.path.toStringWithAddress(RemoteAddressExtension(system).address))

  //  /**
  //   * Convenience method that gets the location service information for a given set of services.
  //   *
  //   * @param connections set of requested connections
  //   * @param system      the caller's actor system
  //   * @return a future object describing the services found
  //   */
  //  def resolve(connections: Set[Connection])(implicit system: ActorRefFactory, timeout: Timeout): Future[LocationsReady] = {
  //    import akka.pattern.ask
  //    val actorRef = system.actorOf(LocationTrackerWorker.props(None))
  //    (actorRef ? LocationTrackerWorker.TrackConnections(connections)).mapTo[LocationsReady]
  //  }
}