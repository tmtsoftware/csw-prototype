package javacsw.util.config;

import csw.util.config.UnitsOfMeasure.*;

/**
 * Java API to UnitsOfMeasure
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class JUnitsOfMeasure {

    public static final Units NoUnits = NoUnits$.MODULE$;
    public static final Units Meters = meters$.MODULE$;
    public static final Units Deg = degrees$.MODULE$;
    public static final Units Seconds = seconds$.MODULE$;
    public static final Units Milliseconds = milliseconds$.MODULE$;

    public static Units fromString(String name) {
        return Units$.MODULE$.fromString(name);
    }
}
