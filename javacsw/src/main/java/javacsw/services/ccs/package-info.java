/**
 * Java API for the Command and Control Service.
 * <p>
 * JHcdController and JAssemblyController are base classes for HCD and Assembly actors.
 * <p>
 * Note that in most cases you should use {@link javacsw.services.ccs.JHcdController} and
 * {@link javacsw.services.ccs.JAssemblyController} instead,
 * since they provide support for lifecycle management and location services.
 * <p>
 * Example JHcdController usage (See test cases in this package):
 * <pre> {@code
 * static class TestHcdController extends JHcdController {
 *      ActorRef worker = getContext().actorOf(TestWorker.props());
 *      LoggingAdapter log = Logging.getLogger(system, this);
 *
 *      // Used to create the TestHcdController actor
 *      public static Props props() {
 *          return Props.create(new Creator<TestHcdController>() {
 *              private static final long serialVersionUID = 1L;
 *
 *              public TestHcdController create() throws Exception {
 *                  return new TestHcdController();
 *              }
 *          });
 *      }
 *
 *      // Send the config to the worker for processing
 *      public void process(Setup config) {
 *          worker.tell(config, self());
 *      }
 *
 *      // Ask the worker actor to send us the current state (handled by parent trait)
 *      public void requestCurrent() {
 *          worker.tell(new TestWorker.RequestCurrentState(), self());
 *      }
 *
 *      public PartialFunction<Object, BoxedUnit> receive() {
 *          return controllerReceive();
 *      }
 *  }
 * } </pre>
 */
package javacsw.services.ccs;