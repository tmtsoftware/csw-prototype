package csw.util.cfg;

import csw.util.cfg.JObserveEvent;
import org.junit.Test;
import csw.util.cfg.Events.EventTime;

import java.util.Date;

/**
 * Tests using the config based classes from Java (Experimental)
 */
public class ConfigJavaTest {
    @Test
    public void testEvent() throws Exception {
        String prefix = "";
        EventTime t = new EventTime(new Date().getTime());

//        // Define a key for an event id
//        Key eventNum = Key$.MODULE$.<Double>create("eventNum");

//        // Define a key for image data
//        val imageData = Key.create[Array[Short]]("imageData")


        JObserveEvent oe = JConfigurations.createObserveEvent(prefix, t)
                .set(StandardKeys.exposureTime(), 1.0)
                .set(StandardKeys.exposureType(), StandardKeys.FLAT$.MODULE$)
                .set(StandardKeys.exposureClass(), StandardKeys.DAYTIME_CALIBRATION$.MODULE$);

        assert(oe.getAsDouble(StandardKeys.exposureTime()).getAsDouble() == 1.0);
    }
}

