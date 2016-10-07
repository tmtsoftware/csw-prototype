package javacsw.services.alarms;

import csw.services.alarms.AscfValidation;

import java.io.File;
import java.util.List;

/**
 * Java blocking admin API for the alarm service.
 */
public interface IBlockingAlarmAdmin {
  /**
   * Initializes the alarm data in the database using the given file
   *
   * @param inputFile the alarm service config file containing info about all the alarms
   * @param reset     if true, delete the current alarms before importing (default: false)
   * @return a list of problems that occurred while validating the config file or ingesting the data into the database
   */
  List<AscfValidation.Problem> initAlarms(File inputFile, boolean reset);

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  void shutdown();
}
