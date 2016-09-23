package csw.examples.vsliceJava.shared;


import javacsw.services.apps.containerCmd.JContainerCmd;

import java.util.Arrays;
import java.util.Optional;

/**
 * Starts the HCD and/or assembly as a standalone application.
 * The first argument can be -s or --start with the value "hcd" or "assembly" to indicate that the HCD or
 * assembly should be started alone. The default is to run both in the same JVM.
 */
public class TromboneApp {
  public static void main(String[] args) {
    if (args.length >= 2) {
      String opt = args[0];
      String arg = args[1].toLowerCase();
      if ("-s".equals(opt) || "--start".equals(opt)) {
        if ("hcd".equals(arg)) {
          JContainerCmd.createContainerCmd("lgsTromboneHCD", Arrays.copyOfRange(args, 2, args.length-1), Optional.of("lgsTromboneHCD.conf"));
        } else if ("assembly".equals(arg)) {
          JContainerCmd.createContainerCmd("lgsTromboneAssembly", Arrays.copyOfRange(args, 2, args.length-1), Optional.of("lgsTromboneAssembly.conf"));
        } else {
          System.out.println("Error: value of -s or --start option should be 'hcd' or 'assembly'");
          System.exit(1);
        }
      } else {
        JContainerCmd.createContainerCmd("lgsTromboneContainer", args, Optional.of("lgsTromboneContainer.conf"));
      }
    }
  }
}
