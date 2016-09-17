package csw.examples.vsliceJava.shared;


import javacsw.services.apps.containerCmd.JContainerCmd;

import java.util.Arrays;
import java.util.Optional;

/**
 * Starts the HCD and/or assembly as a standalone application.
 * The first argument can be "hcd" or "assembly" to indicate that the HCD or
 * assembly should be started alone. The default is to run both in the same JVM.
 */
public class TromboneApp {
  public static void main(String[] args) {
    boolean handled = false;
    if (args.length > 0) {
      String arg = args[0].toLowerCase();
      if ("hcd".equals(arg)) {
        handled = true;
        JContainerCmd.createContainerCmd("lgsTromboneHCD", Arrays.copyOfRange(args, 1, args.length), Optional.of("lgsTromboneHCD.conf"));
      } else if ("assembly".equals(arg)) {
        handled = true;
        JContainerCmd.createContainerCmd("lgsTromboneAssembly", Arrays.copyOfRange(args, 1, args.length), Optional.of("lgsTromboneAssembly.conf"));
      }
    }
    if (!handled) {
      JContainerCmd.createContainerCmd("lgsTromboneContainer", args, Optional.of("lgsTromboneContainer.conf"));
    }
  }
}
