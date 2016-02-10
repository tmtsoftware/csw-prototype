Location Service
=====================

(__Note: ipv6 is not currently supported: Please add -Djava.net.preferIPv4Stack=true to vm options at runtime!__)

The Location Service implemented in this project is based on Multicast DNS.
The necessary support for this should already be available on Mac and Linux machines.
The Location Service helps you to find out the hostname and port number for a service,
as well as other information for determining the correct URI to use, such as the path,
the actor system name and the config prefix.

Two types of services are currently supported: Akka/actor based and HTTP based services.
To register an Akka actor based service, you can use code like this:

    LocationService.registerAkkaService(serviceId, self, "test.akka.prefix")

Where self is a reference to the services own actorRef and the prefix argument indicates the
part of a configuration the actor is interested in receiving.

To register an HTTP based service, you can make a call like this:

    LocationService.registerHttpService(serviceId, port)
    
Here you specify the port and the DNS name for the local host is automatically determined.


Using the Location Service
--------------------------

The Location Service actor can be used in an actor based application to be notified whenever
a set of required services is available or not. In the following example, `TestServiceClient`
depends on `TestAkkaService` and `TestHttpService` and will receive a `ServicesReady` message
with the contact information (URI, actorRef, prefix, ...) when they are both available.
If any of the required services goes down, a Disconnected message is sent.

```
class TestServiceClient extends Actor with ActorLogging {
  val serviceRefs = Set(TestAkkaService.serviceRef, TestHttpService.serviceRef)
  context.actorOf(LocationService.props(serviceRefs))

  override def receive: Receive = {
    case ServicesReady(services) =>
      log.info(s"Received services: ${services.values.map(_.serviceRef.serviceId.name).mkString(", ")}")
    
    case Disconnected(serviceRef) =>
      log.info(s"Disconnected service: ${serviceRef.serviceId.name}")
    
    case x =>
      log.error(s"Received unexpected message $x")
  }
}

```

Connection Issues
-----------------

The location service is based on Multicast DNS (mDNS). The server process is running by default on
Mac OS X and CentOS. Make sure the firewall is either disabled or allows port 5353/UDP.

Note: Applications using the location service may need to have these VM options defined when running,
especially when running in a virtual machine (Vmware):

* -Djava.net.preferIPv4Stack=true (due to problems in Akka-2.4 classes handling ipv6 addresses in some cases)

* -Dakka.remote.netty.tcp.hostname=XXX.XXX.XXX.XX (To make sure Akka uses the correct IP address, in case there are multiple interfaces)

You can avoid having to specify the IP address by calling `LocationService.initAkkaRemoteHostname()` once before creating any akka based services.
It tries to choose the correct IP address and ignore the obviously wrong ones and sets the akka.remote.netty.tcp.hostname system property.

If you run into trouble running a test, such as the location service test, you could try adding the above -D options
in Idea or Eclipse, or from the command line:

  sbt 'project loc' '; set javaOptions += "-Dakka.remote.netty.tcp.hostname=192.168.178.31" ; test'

(Replacing 192.168.178.31 with your primary IP address).

Command Line Tools
------------------

On a Mac, you can use the dns-sd command to view information about registered services.
For example:

    dns-sd -B _csw._tcp 

will continuously display CSW services added or removed, while:

    dns-sd -L TestAkkaService-assembly-akka _csw._tcp

will list information about the application with the DNS name `TestAkkaService-assembly-akka` (not case sensitive).

`_csw._tcp` is the mDNS type used for all CSW services. The default domain is `local.`.

The CSW/mDNS application names here are in the format: *name-serviceType-accessType*,
where *serviceType* is assembly, hcd, etc. and *accessType* is `http` or `akka`.


