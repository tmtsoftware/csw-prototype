Location Service
=====================

The Location Service helps you to find out the hostname and port number for a service,
as well as other information for determining the correct URI to use, such as the path,
the actor system name and the config prefix.

The location service is based on Multicast DNS (mDNS). The server process is running by default on
Mac OS X and Linux. Make sure the firewall is either disabled or allows port 5353/UDP.

Important
---------

Before starting any services that use the location service, this method should be called once:

    LocationService.initInterface()

This determines the primary IP address of the local host and sets some system variables that 
control which IP address is used. If you forget to call this method, there is a chance that 
the wrong IP address will be advertised (there is often more than one).

If, for some reason, the wrong IP address is being used by the location service to register services, 
you can override the IP address by setting the environment variable CSW_HOST to the correct IP address to use.
This might be necessary if the server has multiple IP addresses and you want to use a specific one that is
not chosen automatically.

IPv6 Addresses
--------------

The Location Service currently has problems with IPv6 addresses.
To avoid problems, add this option when running applications using the Location Service:

    -Djava.net.preferIPv4Stack=true

Note that it is not enough to just set the system property once the application is running.
It needs to be on the command line to the application.

Avoiding Conflicts During Development
--------------------------------------

When a service is registered with the location service, it is registered for the entire local network.
In order to avoid conflicts when multiple developers are testing in the same network, you can define
the environment variable or system property `CSW_SERVICE_PREFIX` for all clients and services. This prepends
the given string to the name used to register and look up services.

Service Types
-------------

Two types of services are currently supported: Akka/actor based and HTTP based services.
To register an Akka actor based service, you can use code like this:

    LocationService.registerAkkaConnection(componentId, self, prefix)(context.system)

Where self is a reference to the services own actorRef and the prefix argument indicates the
part of a configuration the actor is interested in receiving.

To register an HTTP based service, you can make a call like this:

    LocationService.registerHttpConnection(componentId, port)

Here you specify the port and the DNS name for the local host is automatically determined.


Using the Location Service
--------------------------

The easiest way to use the location service to keep track of connections to other applications
is to inherit from the `LocationTrackerClientActor` trait and override the `allResolved` method
to be notified with connection details once (and whenever) all connections have been resolved.

In the following example, `TestServiceClient` depends on `TestAkkaService` and `TestHttpService`:

```
class TestServiceClient extends Actor with ActorLogging with LocationTrackerClientActor {
  val connections = Set(TestAkkaService.connection(i), TestHttpService.connection(i))
  connections.foreach(trackConnection)

  override def receive: Receive = trackerClientReceive orElse {
    case x ⇒
      log.error(s"Received unexpected message $x")
  }

  override protected def allResolved(locations: Set[Location]): Unit = {
    log.info(s"Received services: ${connections.map(_.componentId.name).mkString(", ")}")
  }
}
```

Command Line Tools
------------------

On a Mac, you can use the dns-sd command to view information about registered services.
In Linux the command is avahi-browse.
For example, on a mac:

    dns-sd -B _csw._tcp 

will continuously display CSW services added or removed, while:

    dns-sd -L TestAkkaService-assembly-akka _csw._tcp

will list information about the application with the DNS name `TestAkkaService-assembly-akka` (not case sensitive).

`_csw._tcp` is the mDNS type used for all CSW services. The default domain is `local.`.

The CSW/mDNS application names here are in the format: *name-serviceType-accessType*,
where *serviceType* is assembly, hcd, etc. and *accessType* is `http` or `akka`.


