package csw.services.apps.csClient

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import csw.services.cs.akka.ConfigServiceActor._
import csw.services.cs.akka.ConfigServiceClient
import csw.services.cs.core.ConfigId
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Command line client for the config service.
 */
object CsClient extends App {
  if (args.length == 0) {
    usage()
  }
  implicit val system = ActorSystem()
  implicit val timeout: Timeout = 30.seconds

  import system.dispatcher

  val f = for {
    cs ← locateConfigService()
  } yield ConfigServiceClient(cs)
  f.onComplete {
    case Success(client) =>
      commandLine(client, args)
    case Failure(ex) =>
      error(s"Failed to locate config service")
      System.exit(1)
  }

  private def usage(): Unit = {
    error(
      """
        |Usage: (Note: -D options override default values in resources/application.conf)
        |
        | csClient [-Dcsw.services.cs.name=MyConfigServiceName
        |           -Dcsw.services.cs.main-repository=http://myHost/MyMainRepo/
        |           -Dcsw.services.cs.local-repository=/myPath/MyLocalRepo]
        |           $command [args]
        |
        | Where command is one of:
        |
        |  get $path [$id]    # gets file with given path (and optional id) from cs and writes it to stdout
        |  create $path       # creates file with given path by reading stdin
        |  update $path [$id] # updates file with given path (and optional id) by reading stdin
        |  list               # lists the files in the repository
        |  history $path      # shows the history of the given path
        |
        | In addition:
        |
        |  init $path         # creates a new bare Git (main) repository at the given directory path
        |
      """.stripMargin)
  }

  private def error(msg: String): Unit = {
    System.err.println(msg)
    System.exit(1)
  }

  private def commandLine(client: ConfigServiceClient, args: Array[String]): Unit = {
    args(0) match {
      case "get" => get()
      case _ => usage()
    }

    def get(): Unit = {
      val path = new File(args(1))
      val idOpt = if (args.length > 2) Some(ConfigId(args(2))) else None
      for {
        clientDataOpt <- client.get(path, idOpt)
      } {
        clientDataOpt match {
          case Some(clientData) => clientData.writeToOutputStream(System.out)
          case None => error(s"$path not found")
        }
      }
    }

//    def create(): Unit = {
//      val path = new File(args(1))
//      client.create(path, configData, oversize, comment)
//    }

  }
}
