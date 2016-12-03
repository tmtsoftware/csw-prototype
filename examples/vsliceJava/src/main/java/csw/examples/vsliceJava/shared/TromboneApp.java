package csw.examples.vsliceJava.shared;


import javacsw.services.apps.containerCmd.JContainerCmd;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Starts the HCD and/or assembly as a standalone application.
 * The first argument can be -s or --start with the value "hcd" or "assembly" to indicate that the HCD or
 * assembly should be started alone. The default is to run both in the same JVM.
 */
public class TromboneApp {
  public static void main(String[] args) {

    // This defines the names that can be used with the --start option and the config files used ("" is the default entry)
    Map<String, String> m = new HashMap<>();
    m.put("hcd", "tromboneHCD.conf");
    m.put("assembly", "tromboneAssembly.conf");
    m.put("both", "tromboneContainer.conf");
    m.put("", "tromboneContainer.conf"); // default value

    // Parse command line args for the application (app name is vslice, like the sbt project)
    JContainerCmd.createContainerCmd("vslicejava", args, m);
  }
}
