Java APIs for ccs (Command and Control Service)
-----------------------------------------------

See the [ccs](../ccs) project for an overview.

* [JAssemblyController](src/main/java/javacsw/services/ccs/JAssemblyController.java) - parent class
                        for an Assembly actor that accepts configurations and communicates with
                        one or more HCDs or other assemblies before replying with a command status.

* [JHcdController](src/main/java/javacsw/services/ccs/JHcdController.java) - parent class for an HCD actor

* [JHcdStatusMatcherActorFactory](src/main/java/javacsw/services/ccs/JHcdStatusMatcherActorFactory.java) - Subscribes
                           to the current state values of a set of HCDs and notifies the
                           replyTo actor with the command status when they all match the respective demand states,
                           or with an error status message if the given timeout expires.

Here is an example HCD controller:

```
    class TestHcdController extends JHcdController {
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
        public void process(Setup config) {
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

