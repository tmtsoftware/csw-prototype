package javacsw.util.config.tests;

import csw.util.config.*;
import csw.util.config.Configurations.ObserveConfig;
import csw.util.config.Configurations.SetupConfig;
import javacsw.util.config.JItems;
import javacsw.util.config.JUnitsOfMeasure;
import org.junit.Test;

import java.util.*;

import static javacsw.util.config.JItems.*;
import static javacsw.util.config.JConfigDSL.*;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

/**
 * Tests the Java API to the config classes
 */
@SuppressWarnings({"OptionalGetWithoutIsPresent", "unused"})
public class JConfigDSLTests {
    private static final String s1 = "encoder";
    private static final String s2 = "filter";
    private static final String s3 = "detectorTemp";

    private static final String ck = "wfos.blue.filter";
    private static final String ck1 = "wfos.prog.cloudcover";
    private static final String ck2 = "wfos.red.filter";
    private static final String ck3 = "wfos.red.detector";

    private static final IntKey k1 = IntKey("encoder");
    private static final IntKey k2 = IntKey("windspeed");

    @Test
    public void testSetupConfig() {
        SetupConfig sc1 = jadd(sc(ck3), jset(k1, 22), jset(k2, 44));
        assertTrue(sc1.size() == 2);
        assertTrue(sc1.exists(k1));
        assertTrue(sc1.exists(k2));
        assertTrue(jvalue(jitem(sc1, k1)).equals(22));
        assertEquals(jvalues(jitem(sc1, k2)), Collections.singletonList(44));
    }

    @Test
    public void testObserveConfig() {
        ObserveConfig oc1 = jadd(oc(ck3), jset(k1, 22), jset(k2, 44));
        assertTrue(oc1.size() == 2);
        assertTrue(oc1.exists(k1));
        assertTrue(oc1.exists(k2));
        assertTrue(jvalue(jitem(oc1, k1)).equals(22));
        assertEquals(jvalues(jitem(oc1, k2)), Collections.singletonList(44));
    }
}

