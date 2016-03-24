package csw.services

/**
 * == Location Service ==
 *
 * The Location Service implemented in this project is based on Multicast DNS.
 * The necessary support for this should already be available on Mac and Linux machines.
 * The Location Service helps you to find out the hostname and port number for a service,
 * as well as other information for determining the correct URI to use, such as the path,
 * the actor system name and the config prefix.
 *
 * Two types of services are currently supported: Akka/actor based and HTTP based services.
 * To register an Akka actor based service, you can use code like this:
 *
 *     {{{LocationService.registerAkkaService(componentId, self, "test.akka.prefix")}}}
 *
 * Where self is a reference to the services own actorRef and the prefix argument indicates the
 * part of a configuration the actor is interested in receiving.
 *
 * To register an HTTP based service, you can make a call like this:
 *
 *     {{{LocationService.registerHttpService(componentId, port)}}}
 *
 * Here you specify the port and the DNS name for the local host is automatically determined.
 *
 *
 * === Using the Location Service ===
 *
 * The Location Service actor can be used in an actor based application to be notified whenever
 * a set of required services is available or not. In the following example, `TestServiceClient`
 * depends on `TestAkkaService` and `TestHttpService` and will receive a `ServicesReady` message
 * with the contact information (URI, actorRef, prefix, ...) when they are both available.
 * If any of the required services goes down, a Disconnected message is sent.
 *
 * {{{
 * class TestServiceClient extends Actor with ActorLogging {
 *   val connections = Set(TestAkkaService.connection, TestHttpService.connection)
 *   context.actorOf(LocationService.props(connections))
 *
 *   override def receive: Receive = {
 *     case ServicesReady(services) =>
 *       log.info(s"Received services: \${services.values.map(_.connection.componentId.name).mkString(", ")}")
 *
 *     case Disconnected(connection) =>
 *       log.info(s"Disconnected service: \${connection.componentId.name}")
 *
 *     case x =>
 *       log.error(s"Received unexpected message \$x")
 *   }
 * }
 * }}}
 *
 *
 * === Command Line Tools ===
 *
 * On a Mac, you can use the dns-sd command to view information about registered services.
 * For example:
 *
 *     {{{dns-sd -B _csw._tcp}}}
 *
 * will continuously display CSW services added or removed, while:
 *
 *     {{{dns-sd -L TestAkkaService-assembly-akka _csw._tcp}}}
 *
 * will list information about the application with the DNS name `TestAkkaService-assembly-akka` (not case sensitive).
 *
 * `_csw._tcp` is the mDNS type used for all CSW services. The default domain is `local.`.
 *
 * The CSW/mDNS application names here are in the format: ''name-componentType-connectionType'',
 * where ''componentType'' is assembly, hcd, etc. and ''connectionType'' is `http` or `akka`.
 */
package object loc {

}
