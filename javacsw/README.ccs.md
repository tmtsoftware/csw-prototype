Java APIs for ccs (Command and Control Service)
-----------------------------------------------

* JAssemblyController - parent class for an Assembly actor that accepts configurations and communicates with
                        one or more HCDs or other assemblies before replying with a command status.
                        Note: You usually will want to use
                        [JAssemblyControllerWithLifecycleHandler](src/main/java/javacsw/services/pkg/JHcdControllerWithLifecycleHandler.java)
                        instead, since it adds in supervisor/lifecycle support.

* JHcdController - parent class for an HCD actor

* JHcdStatusMatcherActorFactory - Subscribes to the current state values of a set of HCDs and notifies the
                                  replyTo actor with the command status when they all match the respective demand states,
                                   or with an error status message if the given timeout expires.


Here is an example HCD controller:

```
    sclass TestHcdController extends JHcdController {
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
        @Override
        public void process(SetupConfig config) {
            worker.tell(config, self());
        }

        // Ask the worker actor to send us the current state (reply is handled by parent class)
        @Override
        public void requestCurrent() {
            worker.tell(new TestWorker.RequestCurrentState(), self());
        }

        // Use the default actor receive method for HCDs
        @Override
        public PartialFunction<Object, BoxedUnit> receive() {
            return controllerReceive();
        }
    }

```

