/**
 * The Location Service helps you to find out the hostname and port number for a service,
 * as well as other information for determining the correct URI to use, such as the path,
 * the actor system name and the config prefix.
 * <p>
 * The location service is based on Multicast DNS (mDNS). The server process is running by default on
 * Mac OS X and Linux. Make sure the firewall is either disabled or allows port 5353/UDP.
 * <p>
 * <strong>Important</strong>
 * <p>
 * Before starting any services that use the location service, this method should be called once:
 *
 * <pre> {@code
 * LocationService.initInterface()
 * } </pre>
 *
 * This determines the primary IP address of the local host and sets some system variables that
 * control which IP address is used. If you forget to call this method, there is a chance that 
 * the wrong IP address will be advertised (there is often more than one).
 * <p>
 * If you want to specify the IP address yourself, you can also call it like this:
 *
 * <pre> {@code
 * LocationService.initInterface(hostnameOrIpAddress)
 * } </pre>
 *
 * <strong>Service Types</strong>
 * <p>
 * Two types of services are currently supported: Akka/actor based and HTTP based services.
 * To register an Akka actor based service, you can use code like this:
 *
 * <pre> {@code
 *   ComponentId componentId = JComponentId.componentId(assemblyName, Assembly);
 *   AkkaConnection akkaConnection = JConnection.akkaConnection(componentId);
 *   AkkaRegistration akkaRegister = JLocationService.getAkkaRegistration(akkaConnection, self(), prefix);
 * } </pre>
 *
 * Where self is a reference to the services own actorRef and the prefix argument indicates the
 * part of a configuration the actor is interested in receiving.
 * <p>
 * To register an HTTP based service, you can make a call like this:
 *
 * <pre> {@code
 *   HttpRegistration httpRegister = JLocationService.getHttpRegistration(httpConnection, port, path);
 * } </pre>
 *
 * Here you specify the port number and URL path for the http server. The hostname is automatically determined.
 * <p>
 * <strong>Using the Location Service as a Client</strong>
 * <p>
 * In Java, the easiest way to use the location service to keep track of connections to other applications
 * is to inherit from a class that inherits the Scala {@link csw.services.loc.LocationTrackerClientActor}
 * trait (such as {@link javacsw.services.pkg.JAssemblyControllerWithLifecycleHandler}).
 * Then you can override the `allResolved` method to be notified with connection details once (and whenever)
 * all connections have been resolved.
 * <p>
 * In the following example, `TestServiceClient` depends on `TestAkkaService` and `TestHttpService`:
 *
 * <pre> {@code
 * class TestServiceClient extends Actor with ActorLogging with LocationTrackerClientActor {
 * val connections = Set(TestAkkaService.connection(i), TestHttpService.connection(i))
 * connections.foreach(trackConnection)
 *
 * override def receive: Receive = trackerClientReceive orElse {
 * case x ⇒
 * log.error(s"Received unexpected message $x")
 * }
 *
 * override protected def allResolved(locations: Set[Location]): Unit = {
 * log.info(s"Received services: ${connections.map(_.componentId.name).mkString(", ")}")
 * }
 * }
 * } </pre>
 *
 * <strong>Command Line Tools</<strong>
 *
 * On a Mac, you can use the dns-sd command to view information about registered services.
 * For example:
 *
 * <pre> {@code
 * dns-sd -B _csw._tcp
 * } </pre>
 * will continuously display CSW services added or removed, while:
 *
 * <pre> {@code
 * dns-sd -L TestAkkaService-assembly-akka _csw._tcp
 * } </pre>
 * will list information about the application with the DNS name `TestAkkaService-assembly-akka` (not case sensitive).
 *
 * <pre> {@code
 * `_csw._tcp` is the mDNS type used for all CSW services. The default domain is `local.`.
 * } </pre>
 * The CSW/mDNS application names here are in the format: *name-serviceType-accessType*,
 * where *serviceType* is assembly, hcd, etc. and *accessType* is `http` or `akka`.
 */
package javacsw.services.loc;