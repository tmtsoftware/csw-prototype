package javacsw.services.ccs;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import csw.services.ccs.CommandStatus;
import csw.services.ccs.HcdController.Submit;
import csw.util.cfg.Configurations.SetupConfig;
import csw.util.cfg.RunId;
import csw.util.cfg.StateVariable.CurrentState;
import csw.util.cfg.StateVariable.DemandState;
import javacsw.util.cfg.JConfigurations;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;
import scala.runtime.BoxedUnit;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static javacsw.util.cfg.JStandardKeys.position;

/**
 * Tests the java API of HcdController.
 */
public class JHcdControllerTests {

    static final String testPrefix1 = "wfos.blue.filter";
    static final String testPrefix2 = "wfos.red.filter";

    private static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    static class TestHcdController extends JHcdController {
        ActorRef worker = getContext().actorOf(TestWorker.props());
        LoggingAdapter log = Logging.getLogger(system, this);

        // Used to create the TestHcdController actor
        public static Props props() {
            return Props.create(new Creator<TestHcdController>() {
                private static final long serialVersionUID = 1L;

                @Override
                public TestHcdController create() throws Exception {
                    return new TestHcdController();
                }
            });
        }

        // Send the config to the worker for processing
        // XXX has to be public, since inheriting from scala protected...
        @Override
        public void process(SetupConfig config) {
            worker.tell(config, self());
        }

        // Ask the worker actor to send us the current state (handled by parent trait)
        @Override
        public void requestCurrent() {
            worker.tell(new TestWorker.RequestCurrentState(), self());
        }

        @Override
        public PartialFunction<Object, BoxedUnit> receive() {
            return controllerReceive();
        }
    }


    // -- Test worker actor that simulates doing some work --
    static class TestWorker extends AbstractActor {
        LoggingAdapter log = Logging.getLogger(getContext().system(), this);

        // Simulate getting the initial state from the device
        CurrentState initialState = JConfigurations.createCurrentState(testPrefix1).set(position, "None").configType();

        // Simulated current state
        CurrentState currentState = initialState;

        // Used to create the TestWorker actor
        public static Props props() {
            return Props.create(new Creator<TestWorker>() {
                private static final long serialVersionUID = 1L;

                @Override
                public TestWorker create() throws Exception {
                    return new TestWorker();
                }
            });
        }

        // Message sent when simulated work is done
        static class WorkDone {
            SetupConfig config;

            WorkDone(SetupConfig config) {
                this.config = config;
            }
        }

        // Message to request the current state values
        static class RequestCurrentState {
        }

        public TestWorker() {
            receive(ReceiveBuilder.
                    match(SetupConfig.class, this::handleSetupConfig).
                    match(RequestCurrentState.class, rcs -> handleRequestCurrentState()).
                    match(WorkDone.class, wd -> handleWorkDone(wd.config)).
                    match(Object.class, t -> log.warning("Unknown message received: " + t)).
                    build());
        }

        // Simulate doing work
        private void handleSetupConfig(SetupConfig config) {
            log.info("Start processing " + config);
            FiniteDuration d = FiniteDuration.create(2, TimeUnit.SECONDS);
            getContext().system().scheduler().scheduleOnce(d, self(), new WorkDone(config), system.dispatcher(), null);
        }

        private void handleRequestCurrentState() {
            log.info("Requested current state");
            getContext().parent().tell(currentState, self());
        }

        private void handleWorkDone(SetupConfig config) {
            log.info("Done processing " + config);
            currentState = JConfigurations.createCurrentState(config).configType();
            getContext().parent().tell(currentState, self());
        }
    }


    // Tests sending a DemandState to a test HCD, then starting a matcher actor to subscribe
    // to the current state (a state variable updated by the HCD). When the current state matches
    // the demand state, the matcher actor replies with a message (containing the current state).
    //
    // Note: Test requires that Redis is running externally
    @Test
    public void testHcdController() throws Exception {
        new JavaTestKit(system) {
            {
                LoggingAdapter log = Logging.getLogger(system, this);
                ActorRef hcdController = system.actorOf(TestHcdController.props());
                // Send a setup config to the HCD
                SetupConfig config = JConfigurations.createSetupConfig(testPrefix2).set(position, "IR3").configType();
                hcdController.tell(new Submit(config), getRef());
                DemandState demand = JConfigurations.createDemandState(config).configType();

                // Create an actor to subscribe and wait for the HCD to get to the demand state
                BiFunction<DemandState, CurrentState, Boolean> matcher = (d, c) -> Objects.equals(d.prefix(), c.prefix()) && Objects.equals(d.data(), c.data());
                JHcdController.getHcdStatusMatcherActor(
                        system, Collections.singletonList(demand), Collections.singleton(hcdController), getRef(),
                        RunId.create(), new Timeout(5, TimeUnit.SECONDS), matcher);

                new Within(JavaTestKit.duration("10 seconds")) {
                    protected void run() {
                        CommandStatus status = expectMsgClass(CommandStatus.Completed.class);
                        log.info("Done (2). Received reply from matcher with current state: " + status);
                    }
                };
            }
        };
    }

}
