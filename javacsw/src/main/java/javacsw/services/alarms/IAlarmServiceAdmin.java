package javacsw.services.alarms;

import csw.services.alarms.AscfValidation;
import scala.Unit;
import scala.concurrent.ExecutionContext;
import scala.runtime.BoxedUnit;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Java admin API for the alarm service.
 */
@SuppressWarnings("SameParameterValue")
public interface IAlarmServiceAdmin {
  /**
   * Initializes the alarm data in the database using the given file
   *
   * @param inputFile the alarm service config file containing info about all the alarms
   * @param reset     if true, delete the current alarms before importing (default: false)
   * @return a future list of problems that occurred while validating the config file or ingesting the data into the database
   */
  CompletableFuture<List<AscfValidation.Problem>> initAlarms(File inputFile, boolean reset);

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
   *   tracklocation --name "Alarm Service Test" --command "redis-server --port %port" --no-exit
   *   }}}
   *
   * This method is mainly for use by tests. In production, you would use the tracklocation app
   * to start Redis once.
   *
   * @param name The name to use to register the alarm service with the location service
   * @param noExit if true, do not exit the application when redis exists
   * @param ec required for futures
   * @return a future that completes when the redis server exits
   */
  static CompletableFuture<BoxedUnit> startAlarmService(String name, Boolean noExit, ExecutionContext ec) {
    return JAlarmServiceAdmin.startAlarmService(name, noExit, ec);
  }
}
