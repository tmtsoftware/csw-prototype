package javacsw.util.config;

import csw.util.config.UnitsOfMeasure.*;

/**
 * Java API to UnitsOfMeasure
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class JUnitsOfMeasure {

    public static final Units NoUnits = NoUnits$.MODULE$;
    public static final Units Meters = Meters$.MODULE$;
    public static final Units Deg = Deg$.MODULE$;
    public static final Units Seconds = Seconds$.MODULE$;
    public static final Units Milliseconds = Milliseconds$.MODULE$;

    public static Units fromString(String name) {
        return Units$.MODULE$.fromString(name);
    }
}
