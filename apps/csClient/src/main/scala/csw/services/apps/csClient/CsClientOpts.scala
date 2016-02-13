package csw.services.apps.csClient

import java.io.File

/**
 * Command line argument parsing for CsClient
 */
object CsClientOpts {

  /**
   * Command line options
   * @param config optional config service config file
   * @param subcmd subcommand (create, update, etc.)
   * @param path path in the git repo
   * @param inputFile file to read data from
   * @param outputFile file to write data to
   * @param id optional file version id
   * @param oversize set to true to use large/binary file handling
   * @param comment optional create or update comment
   */
  case class Config(
    config: Option[File] = None,
    subcmd: String       = "", path: File = null, inputFile: File = null, outputFile: File = null,
    id: Option[String] = None, oversize: Boolean = false, comment: String = ""
  )

  private val parser = new scopt.OptionParser[Config]("scopt") {
    head("csclient", System.getProperty("CSW_VERSION"))

    opt[File]("config") action { (x, c) ⇒
      c.copy(config = Some(x))
    } text "optional config file to use for config service settings"

    cmd("get") action { (_, c) ⇒
      c.copy(subcmd = "get")
    } text "gets file with given path from the config service and writes it to the output file" children (

      arg[File]("<path>") action { (x, c) ⇒
        c.copy(path = x)
      } text "path name in Git repository",

      opt[File]('o', "out") required () valueName "<outputFile>" action { (x, c) ⇒
        c.copy(outputFile = x)
      } text "output file",

      opt[String]("id") action { (x, c) ⇒
        c.copy(id = Some(x))
      } text "optional version id of file to get"
    )

    cmd("exists") action { (_, c) ⇒
      c.copy(subcmd = "exists")
    } text "checks if the path exists in the repository" children (

      arg[File]("<path>") action { (x, c) ⇒
        c.copy(path = x)
      } text "path name in Git repository"
    )

    cmd("create") action { (_, c) ⇒
      c.copy(subcmd = "create")
    } text "creates the file with the given path in the config service by reading the input file" children (

      arg[File]("<path>") action { (x, c) ⇒
        c.copy(path = x)
      } text "path name in Git repository",

      opt[File]('i', "in") required () valueName "<inputFile>" action { (x, c) ⇒
        c.copy(inputFile = x)
      } text "input file",

      opt[Unit]("oversize") action { (_, c) ⇒
        c.copy(oversize = true)
      } text "add this option for large/binary files",

      opt[String]('c', "comment") action { (x, c) ⇒
        c.copy(comment = x)
      } text "optional create comment"
    )

    cmd("update") action { (_, c) ⇒
      c.copy(subcmd = "update")
    } text "updates the file with the given path in the config service by reading the input file" children (

      arg[File]("<path>") action { (x, c) ⇒
        c.copy(path = x)
      } text "path name in Git repository",

      opt[File]('i', "in") required () valueName "<inputFile>" action { (x, c) ⇒
        c.copy(inputFile = x)
      } text "input file",

      opt[String]('c', "comment") action { (x, c) ⇒
        c.copy(comment = x)
      } text "optional create comment"
    )

    cmd("createOrUpdate") action { (_, c) ⇒
      c.copy(subcmd = "createOrUpdate")
    } text "creates or updates the file with the given path in the config service by reading the input file" children (

      arg[File]("<path>") action { (x, c) ⇒
        c.copy(path = x)
      } text "path name in Git repository",

      opt[File]('i', "in") required () valueName "<inputFile>" action { (x, c) ⇒
        c.copy(inputFile = x)
      } text "input file",

      opt[Unit]("oversize") action { (_, c) ⇒
        c.copy(oversize = true)
      } text "add this option for large/binary files",

      opt[String]('c', "comment") action { (x, c) ⇒
        c.copy(comment = x)
      } text "optional create comment"
    )

    cmd("list") action { (_, c) ⇒
      c.copy(subcmd = "list")
    } text "lists the files in the repository" children ()

    cmd("history") action { (_, c) ⇒
      c.copy(subcmd = "history")
    } text "shows the history of the given path" children (

      arg[File]("<path>") action { (x, c) ⇒
        c.copy(path = x)
      } text "path name in Git repository"
    )

    cmd("setDefault") action { (_, c) ⇒
      c.copy(subcmd = "setDefault")
    } text "sets the default version of the file" children (

      arg[File]("<path>") action { (x, c) ⇒
        c.copy(path = x)
      } text "path name in Git repository",

      opt[String]("id") action { (x, c) ⇒
        c.copy(id = Some(x))
      } text "optional version id to set as default for file"
    )

    cmd("resetDefault") action { (_, c) ⇒
      c.copy(subcmd = "resetDefault")
    } text "resets the default to the latest version of the file" children (

      arg[File]("<path>") action { (x, c) ⇒
        c.copy(path = x)
      } text "path name in Git repository"
    )

    cmd("getDefault") action { (_, c) ⇒
      c.copy(subcmd = "getDefault")
    } text "gets the default version of the file" children (

      arg[File]("<path>") action { (x, c) ⇒
        c.copy(path = x)
      } text "path name in Git repository",

      opt[File]('o', "out") required () valueName "<outputFile>" action { (x, c) ⇒
        c.copy(outputFile = x)
      } text "output file"
    )

    checkConfig { c ⇒
      if (c.subcmd.isEmpty) failure("Please specify one (get, create, update, list, history)") else success
    }
  }

  /**
   * Parses the command line arguments and returns a value if they are valid.
   * @param args the command line arguments
   * @return an object containing the parsed values of the command line arguments
   */
  def parse(args: Seq[String]): Option[Config] = parser.parse(args, Config())
}
