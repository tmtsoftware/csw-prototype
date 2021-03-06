Java API for the Location Service
=================================

See the [loc](../loc) project for an overview.

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

If you want to specify the IP address yourself, you can also call it like this:

    LocationService.initInterface(hostnameOrIpAddress)

Service Types

Two types of services are currently supported: Akka/actor based and HTTP based services.
To register an Akka actor based service, you can use code like this:

```
  ComponentId componentId = JComponentId.componentId(assemblyName, Assembly);
  JLocationService.registerAkkaConnection(componentId, self(), prefix, getContext().system());
```

Where self is a reference to the services own actorRef and the prefix argument indicates the
part of a configuration the actor is interested in receiving.

To register an HTTP based service, you can make a call like this:
```
    JLocationService.registerHttpConnection(componentId, port, uriPath, getContext().system());
```
Here you specify the port number and URI path for the http server. The hostname is automatically determined.
In both cases the return value from the register method can be used to later unregister from the location service.

Using the Location Service as a Client
--------------------------------------

In Java, the easiest way to use the location service in an actor to keep track of connections to other applications
is to inherit from a class that inherits the Scala `LocationTrackerClientActor`
trait (such as [AbstractLocationTrackerClientActor](src/main/scala/javacsw/services/loc/AbstractLocationTrackerClientActor.scala).
Then you can override the `allResolved` method to be notified with connection details once (and whenever)
all connections have been resolved.

In the following example, `TestServiceClient` depends on a number of `TestAkkaService-*` and `TestHttpService-*` connections:
```
public class TestServiceClient extends AbstractLocationTrackerClientActor {

    // Used to create the TestServiceClient actor
    static Props props(int numServices) {
        return Props.create(new Creator<TestServiceClient>() {
            private static final long serialVersionUID = 1L;

            public TestServiceClient create() throws Exception {
                return new TestServiceClient(numServices);
            }
        });
    }

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);


    // Constructor: tracks the given number of akka and http connections
    public TestServiceClient(int numServices) {
        for(int i = 0; i < numServices; i++) {
            trackConnection(TestAkkaService.connection(i+1));
            trackConnection(TestHttpService.connection(i+1));
        }

        // Actor messages are handled by the parent class (in trackerClientReceive method)
        receive(trackerClientReceive().orElse(ReceiveBuilder.
                matchAny(t -> log.warning("Unknown message received: " + t)).
                build()));
    }

    // Called when all connections are resolved
    public void allResolved(Set<LocationService.Location> locations) {
        List<String> names = new ArrayList<>();
        locations.forEach(loc -> names.add(loc.connection().componentId().name()));
        log.info("Test Passed: Received services: " + names.stream().collect(Collectors.joining(", ")));
    }

    // If a command line arg is given, it should be the number of (akka, http) pairs of services to start (default: 1 of each).
    // The client and service applications can be run on the same or different hosts.
    public static void main(String[] args) {
        int numServices = 1;
        if (args.length != 0)
            numServices = Integer.valueOf(args[0]);

        LocationService.initInterface();
        ActorSystem system = ActorSystem.create();
        system.actorOf(TestServiceClient.props(numServices));
    }
}
```

Command Line Tools
------------------

On a Mac, you can use the dns-sd command to view information about registered services.
For example:
```
dns-sd -B _csw._tcp
```
will continuously display CSW services added or removed, while:
```
dns-sd -L TestAkkaService-assembly-akka _csw._tcp
```
will list information about the application with the DNS name `TestAkkaService-assembly-akka` (not case sensitive).
`_csw._tcp` is the mDNS type used for all CSW services. The default domain is `local.`.
The CSW/mDNS application names here are in the format: *name-serviceType-accessType*,
where *serviceType* is assembly, hcd, etc. and *accessType* is `http` or `akka`.

