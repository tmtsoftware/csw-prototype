package csw.services.apps.csClient

import akka.actor.ActorSystem
import akka.util.Timeout
import csw.services.cs.akka.{ ConfigServiceActor, ConfigServiceClient, ConfigServiceSettings }
import csw.services.cs.core.{ ConfigData, ConfigId, ConfigManager }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

/**
 * Command line client for the config service.
 * See CsClientOpts for usage. A help message is printed on error.
 */
object CsClient extends App {
  implicit val system = ActorSystem()
  implicit val timeout: Timeout = 30.seconds
  import csw.services.apps.csClient.CsClient.system.dispatcher

  CsClientOpts.parse(args) match {
    case Some(options) ⇒ run(options)
    case None          ⇒ System.exit(1)
  }

  private def run(options: CsClientOpts.Config): Unit = {
    val settings = ConfigServiceSettings(system)

    val f = for {
      cs ← ConfigServiceActor.locateConfigService(settings.name)
      result ← commandLine(ConfigServiceClient(cs), options)
    } yield result

    f.onComplete {
      case Success(client) ⇒
        system.shutdown()
      case Failure(ex) ⇒
        System.err.println(s"Error: ${ex.getMessage}")
        system.shutdown()
        System.exit(1)
    }
  }

  private def commandLine(client: ConfigManager, options: CsClientOpts.Config): Future[Unit] = {

    def get(): Future[Unit] = {
      val idOpt = options.id.map(ConfigId(_))
      for {
        configDataOpt ← client.get(options.path, idOpt)
        if configDataOpt.isDefined
        _ ← configDataOpt.get.writeToFile(options.outputFile)
      } yield ()
    }

    def create(): Future[Unit] = {
      val configData = ConfigData(options.inputFile)
      for {
        configId ← client.create(options.path, configData, oversize = options.oversize, options.comment)
      } yield {
        println(configId.id)
      }
    }

    def update(): Future[Unit] = {
      val configData = ConfigData(options.inputFile)
      for {
        configId ← client.update(options.path, configData, options.comment)
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
      for {
        histList ← client.history(options.path)
      } yield {
        for (h ← histList) {
          println(s"${h.id.id}\t${h.time}\t${h.comment}")
        }
      }
    }

    options.subcmd match {
      case "get"     ⇒ get()
      case "create"  ⇒ create()
      case "update"  ⇒ update()
      case "list"    ⇒ list()
      case "history" ⇒ history()
      case x ⇒
        throw new RuntimeException(s"Unknown subcommand: $x")
    }
  }
}
