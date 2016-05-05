package javacsw.util.cfg;

import csw.util.cfg.Key;
import csw.util.cfg.StandardKeys;

/**
 * Defines standard keys to be used in configurations (from Java).
 */
public class JStandardKeys {

    // -- Exposure classes --
    public static final StandardKeys.ExposureClass SCIENCE = StandardKeys.SCIENCE$.MODULE$;
    public static final StandardKeys.ExposureClass NIGHTTIME_CALIBRATION = StandardKeys.NIGHTTIME_CALIBRATION$.MODULE$;
    public static final StandardKeys.ExposureClass DAYTIME_CALIBRATION = StandardKeys.DAYTIME_CALIBRATION$.MODULE$;
    public static final StandardKeys.ExposureClass ACQUISITION = StandardKeys.ACQUISITION$.MODULE$;

    // -- Exposure types --
    public static final StandardKeys.ExposureType FLAT = StandardKeys.FLAT$.MODULE$;
    public static final StandardKeys.ExposureType ARC = StandardKeys.ARC$.MODULE$;
    public static final StandardKeys.ExposureType BIAS = StandardKeys.BIAS$.MODULE$;
    public static final StandardKeys.ExposureType OBSERVE = StandardKeys.OBSERVE$.MODULE$;

    // -- Cloud cover types --
    public static final StandardKeys.CloudCoverType PERCENT_20 = StandardKeys.PERCENT_20$.MODULE$;
    public static final StandardKeys.CloudCoverType PERCENT_50 = StandardKeys.PERCENT_50$.MODULE$;
    public static final StandardKeys.CloudCoverType PERCENT_70 = StandardKeys.PERCENT_70$.MODULE$;
    public static final StandardKeys.CloudCoverType PERCENT_80 = StandardKeys.PERCENT_80$.MODULE$;
    public static final StandardKeys.CloudCoverType PERCENT_90 = StandardKeys.PERCENT_90$.MODULE$;
    public static final StandardKeys.CloudCoverType ANY = StandardKeys.ANY$.MODULE$;

    // -- Common keys --

    // public static final JKey units = new JKey<UnitsOfMeasure.Units>(StandardKeys.units(), UnitsOfMeasure.Units.class);
    public static final Key units = StandardKeys.units();

    // -- Setup keys --
    public static final Key position = StandardKeys.position();
    public static final Key cloudCover = StandardKeys.cloudCover();

    // -- ObserveConfig --

    public static final Key exposureTime = StandardKeys.exposureTime();
    public static final Key exposureType = StandardKeys.exposureType();
    public static final Key exposureClass = StandardKeys.exposureClass();
    public static final Key repeats = StandardKeys.repeats();

    // -- For tests --
    public static final Key filter = StandardKeys.filter();
    public static final Key disperser = StandardKeys.disperser();

    public static final String filterPrefix = StandardKeys.filterPrefix();
    public static final String disperserPrefix = StandardKeys.disperserPrefix();
}

