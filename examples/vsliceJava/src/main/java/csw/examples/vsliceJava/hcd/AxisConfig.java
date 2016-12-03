package csw.examples.vsliceJava.hcd;

import com.typesafe.config.Config;

/**
 * Axis configuration
 */
public class AxisConfig {
  String axisName;
  int lowLimit;
  int lowUser;
  int highUser;
  int highLimit;
  int home;
  int startPosition;
  int stepDelayMS;

  public AxisConfig(String axisName, int lowLimit, int lowUser, int highUser, int highLimit, int home, int startPosition, int stepDelayMS) {
    this.axisName = axisName;
    this.lowLimit = lowLimit;
    this.lowUser = lowUser;
    this.highUser = highUser;
    this.highLimit = highLimit;
    this.home = home;
    this.startPosition = startPosition;
    this.stepDelayMS = stepDelayMS;
  }

  public AxisConfig(Config config) {
    // Main prefix for keys used below
    String prefix = "csw.examples.trombone.hcd";
    axisName = config.getString(prefix + ".axis-config.axisName");
    lowLimit = config.getInt(prefix + ".axis-config.lowLimit");
    lowUser = config.getInt(prefix + ".axis-config.lowUser");
    highUser = config.getInt(prefix + ".axis-config.highUser");
    highLimit = config.getInt(prefix + ".axis-config.highLimit");
    home = config.getInt(prefix + ".axis-config.home");
    startPosition = config.getInt(prefix + ".axis-config.startPosition");
    stepDelayMS = config.getInt(prefix + ".axis-config.stepDelayMS");
  }
}
