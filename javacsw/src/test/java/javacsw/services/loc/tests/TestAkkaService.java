package javacsw.services.loc.tests;

import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import csw.services.loc.ComponentId;
import csw.services.loc.Connection;
import csw.services.loc.LocationService;
import javacsw.services.loc.JComponentId;
import javacsw.services.loc.JComponentType;
import javacsw.services.loc.JConnection;
import javacsw.services.loc.JLocationService;

/**
 * Starts one or more akka services in order to test the location service.
 * If a command line arg is given, it should be the number of services to start (default: 1).
 * Each service will have a number appended to its name.
 * You should start the TestServiceClient with the same number, so that it
 * will try to find all the services.
 * The client and service applications can be run on the same or different hosts.
 */
public class TestAkkaService extends AbstractActor {
    // Component id for the ith service
    private static ComponentId componentId(int i) {
        return JComponentId.componentId("TestAkkaService-" + i, JComponentType.Assembly);
    }

    // Connection for the ith service
    static Connection connection(int i) {
        return JConnection.akkaConnection(componentId(i));
    }

    // Used to create the ith TestAkkaService actor
    private static Props props(int i) {
        return Props.create(new Creator<TestAkkaService>() {
            private static final long serialVersionUID = 1L;

            @Override
            public TestAkkaService create() throws Exception {
                return new TestAkkaService(i);
            }
        });
    }

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);


    // Constructor: registers self with the location service
    private TestAkkaService(int i) {
        JLocationService.registerAkkaConnection(TestAkkaService.componentId(i), self(), "test.akka.prefix", getContext().system());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .matchAny(t -> log.warning("Unknown message received: " + t))
            .build();
    }

    // main: Starts and registers the given number of services (default: 1)
    public static void main(String[] args) {
        int numServices = 1;
        if (args.length != 0)
            numServices = Integer.valueOf(args[0]);

        LocationService.initInterface();
        ActorSystem system = ActorSystem.create();
        for (int i = 0; i < numServices; i++)
            system.actorOf(TestAkkaService.props(i+1));
    }
}
