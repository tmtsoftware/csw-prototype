package csw.examples.vsliceJava.shared;

/**
  * TMT Source Code: 7/15/16.
  */
@SuppressWarnings("unused")
public class TromboneData {
  public static final String testConf = String.join(System.getProperty("line.separator"),
     "container {",
      "  name = \"Container-2\"",
      "  connectionType: [akka]",
      "  initialDelay = 2 second",
      "  creationDelay = 1 second",
      "  lifecycleDelay = 3 seconds",
      "  components {",
      "    lgsTrombone {",
      "      type = Assembly",
      "      class = csw.examples.vsliceJava.assembly.TromboneAssembly",
      "      prefix = nfiraos.ncc.trombone",
      "      connectionType: [akka]",
      "      connections = [",
      "        // Component connections used by this component",
      "        // Name: ComponentType ConnectionType",
      "        {",
      "          name: lgsTromboneHCD",
      "          type: HCD",
      "          connectionType: [akka]",
      "        }",
      "      ]",
      "      }",
      "      lgsTromboneHCD {",
      "        type = HCD",
      "        class = \"csw.examples.e2e.hcd.TromboneHCD\"",
      "        prefix = nfiraos.ncc.tromboneHCD",
      "        connectionType: [akka]",
      "        rate = 1 second",
      "     }",
      "   }",
      "}"
  );
}

