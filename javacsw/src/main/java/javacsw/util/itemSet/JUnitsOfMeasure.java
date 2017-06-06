package javacsw.util.itemSet;

import csw.util.param.UnitsOfMeasure.*;

/**
 * Java API to UnitsOfMeasure
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class JUnitsOfMeasure {
  public static final Units none = NoUnits$.MODULE$;
  public static final Units encoder = encoder$.MODULE$;

  public static final Units micrometers = micrometers$.MODULE$;
  public static final Units millimeters = millimeters$.MODULE$;
  public static final Units meters = meters$.MODULE$;
  public static final Units kilometers = kilometers$.MODULE$;

  public static final Units degrees = degrees$.MODULE$;
  public static final Units seconds = seconds$.MODULE$;
  public static final Units milliseconds = milliseconds$.MODULE$;

  public static Units fromString(String name) {
    return Units$.MODULE$.fromString(name);
  }
}
