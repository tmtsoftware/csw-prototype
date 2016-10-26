package javacsw.services.events;

import scala.Unit;
import scala.concurrent.ExecutionContext;
import scala.runtime.BoxedUnit;

import java.util.concurrent.CompletableFuture;

/**
 * Java admin API for the event service.
 */
@SuppressWarnings("SameParameterValue")
public interface IEventServiceAdmin {
  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   * @return a future indicating when shutdown has completed
   */
  CompletableFuture<Unit> shutdown();

  /**
   * Starts a redis instance on a random free port (redis-server must be in your shell path)
   * and registers it with the location service.
   * This is the equivalent of running this from the command line:
   *   {{{
   *   tracklocation --name "Event Service Test" --command "redis-server --port %port" --no-exit
   *   }}}
   *
   * This method is mainly for use by tests. In production, you would use the tracklocation app
   * to start Redis once.
   *
   * @param name The name to use to register the event service with the location service
   * @param noExit if true, do not exit the application when redis exists
   * @param ec required for futures
   * @return a future that completes when the redis server exits
   */
  static CompletableFuture<BoxedUnit> startEventService(String name, Boolean noExit, ExecutionContext ec) {
    return JEventServiceAdmin.startEventService(name, noExit, ec);
  }
}
