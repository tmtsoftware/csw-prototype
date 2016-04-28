package javacsw.util.cfg;

import org.junit.Test;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Tests using the config based classes from Java
 */
public class JConfigurationsTest {
    @Test
    public void testObserveEvent() throws Exception {
        String prefix = "my.prefix";

        JObserveEvent oe = JConfigurations.createObserveEvent(prefix)
                .set(JStandardKeys.exposureTime, 1.0)
                .set(JStandardKeys.exposureClass, JStandardKeys.ACQUISITION);

        OptionalDouble d = oe.getAsDouble(JStandardKeys.exposureTime);
        assert (d.isPresent() && d.getAsDouble() == 1.0);

        Optional expClass = oe.get(JStandardKeys.exposureClass);
        assert (expClass.isPresent() && expClass.get()== JStandardKeys.ACQUISITION);
    }

    @Test
    public void testSetupConfig() throws Exception {
        String prefix = "my.prefix";

        JSetupConfig sc = JConfigurations.createSetupConfig(prefix)
                .set(JStandardKeys.filter, "MyFilter")
                .set(JStandardKeys.disperser, "MyDisperser");

        Optional<String> filter = sc.getAsString(JStandardKeys.filter);
        assert (filter.isPresent() && filter.get().equals("MyFilter"));

        Optional<String> disperser = sc.getAsString(JStandardKeys.disperser);
        assert (disperser.isPresent() && disperser.get().equals("MyDisperser"));

    }

}

