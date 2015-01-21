package csw.services.apps.csClient

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import csw.services.cs.akka.{ ConfigServiceSettings, ConfigServiceActor, ConfigServiceClient }
import csw.services.cs.core.{ ConfigData, ConfigId, ConfigManager }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

// XXX TODO: use a command line parser like scopt or scallop, add -config and -comment options
/**
 * Command line client for the config service.
 */
object CsClient extends App {
  implicit val system = ActorSystem()
  implicit val timeout: Timeout = 30.seconds
  import csw.services.apps.csClient.CsClient.system.dispatcher

  if (args.length == 0) {
    usage()
    System.exit(1)
  }

  val settings = ConfigServiceSettings(system)

  val f = for {
    cs ← ConfigServiceActor.locateConfigService(settings.name)
    result ← commandLine(ConfigServiceClient(cs), args)
  } yield result

  f.onComplete {
    case Success(client) ⇒
      system.shutdown()
    case Failure(ex) ⇒
      System.err.println(s"Error: ${ex.getMessage}")
      system.shutdown()
      System.exit(1)
  }

  // XXX TODO add -comment option
  private def usage(): Future[Unit] = {
    Future.successful(System.err.println(
      """
        |Usage: Note: -D options override default values in the config file.
        |       If no config file is given, the default config is used (from resources).
        |
        | csclient [-config file.conf] (XXX TODO)
        |          [-Dcsw.services.cs.name=MyConfigServiceName]
        |           $command [args]
        |
        | Where command is one of:
        |
        |  get $path $outputFile [$id]
        |  - gets file with given path (and optional id) from cs and writes it to $outputFile
        |
        |  create $path $inputFile [-oversize] [-comment $comment]
        |  - creates file with given path by reading $inputFile (add -oversize for special handling)
        |
        |  update $path $inputFile [$id] [-oversize] [-comment $comment]
        |  - updates file with given path (and optional id) by reading $inputFile
        |
        |  list
        |  - lists the files in the repository
        |
        |  history $path
        |  - shows the history of the given path
        |
      """.stripMargin))
  }

  private def commandLine(client: ConfigManager, args: Array[String]): Future[Unit] = {

    def get(): Future[Unit] = {
      val path = new File(args(1))
      val outputFile = new File(args(2))
      val idOpt = if (args.length > 3) Some(ConfigId(args(3))) else None
      for {
        clientDataOpt ← client.get(path, idOpt)
        if clientDataOpt.isDefined
        _ ← clientDataOpt.get.writeToFile(outputFile)
      } yield ()
    }

    def create(): Future[Unit] = {
      val path = new File(args(1))
      val inputFile = new File(args(2))
      val oversize = args.length > 3 && args(3) == "-oversize"
      val configData = ConfigData(inputFile)
      val comment = "" // XXX TODO: add comment handling
      for {
        configId ← client.create(path, configData, oversize = oversize, comment)
      } yield {
        println(configId.id)
      }
    }

    def update(): Future[Unit] = {
      val path = new File(args(1))
      val inputFile = new File(args(2))
      val configData = ConfigData(inputFile)
      val comment = "" // XXX TODO: add comment handling
      for {
        configId ← client.update(path, configData, comment)
      } yield {
        println(configId.id)
      }
    }

    def list(): Future[Unit] = {
      for {
        infoList ← client.list()
      } yield {
        for (i ← infoList) {
          println(s"${i.path}\t${i.id.id}\t${i.comment}")
        }
      }
    }

    def history(): Future[Unit] = {
      val path = new File(args(1))
      for {
        histList ← client.history(path)
      } yield {
        for (h ← histList) {
          println(s"${h.id.id}\t${h.time}\t${h.comment}")
        }
      }
    }

    args(0) match {
      case "get"     ⇒ get()
      case "create"  ⇒ create()
      case "update"  ⇒ update()
      case "list"    ⇒ list()
      case "history" ⇒ history()
      case _         ⇒ usage()
    }
  }
}
