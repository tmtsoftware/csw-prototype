package javacsw.util.cfg;

import javacsw.util.cfg.JObserveEvent;
import csw.util.cfg.StandardKeys;
import org.junit.Test;
import csw.util.cfg.Events.*;

import java.util.Date;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Tests using the config based classes from Java (Experimental)
 */
public class JConfigurationsTest {
    @Test
    public void testEvent() throws Exception {
        String prefix = "";
        EventTime t = new EventTime(new Date().getTime());

        JObserveEvent oe = JConfigurations.createObserveEvent(prefix, t)
                .set(StandardKeys.exposureTime(), 1.0)
                .set(StandardKeys.exposureClass(), JStandardKeys.ACQUISITION());

        OptionalDouble d = oe.getAsDouble(StandardKeys.exposureTime());
        assert (d.isPresent() && d.getAsDouble() == 1.0);

        Optional expClass = oe.get(StandardKeys.exposureClass());
        assert (expClass.isPresent() && expClass.get()== JStandardKeys.ACQUISITION());
    }
}

