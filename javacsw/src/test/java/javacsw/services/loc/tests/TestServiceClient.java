package javacsw.services.loc.tests;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import csw.services.loc.LocationService;
import javacsw.services.loc.AbstractLocationTrackerClientActor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
  * A location service test client application that attempts to resolve one or more sets of
  * akka and http services.
 */
public class TestServiceClient extends AbstractLocationTrackerClientActor {

    // Used to create the ith TestServiceClient actor
    private static Props props(int numServices) {
        return Props.create(new Creator<TestServiceClient>() {
            private static final long serialVersionUID = 1L;

            @Override
            public TestServiceClient create() throws Exception {
                return new TestServiceClient(numServices);
            }
        });
    }

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);


    // Constructor: tracks the given number of akka and http connections
    private TestServiceClient(int numServices) {
        for(int i = 0; i < numServices; i++) {
            trackConnection(TestAkkaService.connection(i+1));
            trackConnection(TestHttpService.connection(i+1));
        }
    }

    @Override
    public Receive createReceive() {
        // Actor messages are handled by the parent class (in trackerClientReceive method)
        return receiveBuilder()
            .matchAny(t -> log.warning("Unknown message received: " + t))
            .build();
    }

    // Called when all connections are resolved
    @Override
    public void allResolved(Set<LocationService.Location> locations) {
        List<String> names = new ArrayList<>();
        locations.forEach(loc -> names.add(loc.connection().componentId().name()));
        log.debug("Test Passed: Received services: " + names.stream().collect(Collectors.joining(", ")));
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
