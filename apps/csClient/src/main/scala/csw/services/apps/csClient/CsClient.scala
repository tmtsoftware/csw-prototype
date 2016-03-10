package csw.services.apps.csClient

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.services.cs.akka.{ConfigServiceActor, ConfigServiceClient, ConfigServiceSettings}
import csw.services.cs.core.{ConfigData, ConfigId, ConfigManager}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Command line client for the config service.
 * See this project's README.md for usage. A help message is printed on error.
 */
object CsClient extends App {
  implicit val system = ActorSystem()
  implicit val timeout: Timeout = 30.seconds

  import system.dispatcher

  CsClientOpts.parse(args) match {
    case Some(options) ⇒ run(options)
    case None          ⇒ System.exit(1)
  }

  private def run(options: CsClientOpts.Config): Unit = {
    val settings = options.config match {
      case Some(configFile) ⇒ ConfigServiceSettings(ConfigFactory.parseFile(configFile))
      case None             ⇒ ConfigServiceSettings(system)
    }

    val f = for {
      cs ← ConfigServiceActor.locateConfigService(settings.name)
      result ← commandLine(ConfigServiceClient(cs, settings.name), options)
    } yield result

    f.onComplete {
      case Success(client) ⇒
        system.terminate()
      case Failure(ex) ⇒
        System.err.println(s"Error: ${ex.getMessage}")
        system.terminate()
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

    def exists(): Future[Unit] = {
      for {
        exists ← client.exists(options.path)
      } yield {
        println(exists)
      }
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

    def createOrUpdate(): Future[Unit] = {
      val configData = ConfigData(options.inputFile)
      for {
        configId ← client.createOrUpdate(options.path, configData, oversize = options.oversize, options.comment)
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

    def setDefault(): Future[Unit] = {
      val idOpt = options.id.map(ConfigId(_))
      for {
        _ ← client.setDefault(options.path, idOpt)
      } yield ()
    }

    def getDefault: Future[Unit] = {
      for {
        configDataOpt ← client.getDefault(options.path)
        if configDataOpt.isDefined
        _ ← configDataOpt.get.writeToFile(options.outputFile)
      } yield ()
    }

    def resetDefault(): Future[Unit] = {
      for {
        _ ← client.resetDefault(options.path)
      } yield ()
    }

    options.subcmd match {
      case "get"            ⇒ get()
      case "exists"         ⇒ exists()
      case "create"         ⇒ create()
      case "update"         ⇒ update()
      case "createOrUpdate" ⇒ createOrUpdate()
      case "list"           ⇒ list()
      case "history"        ⇒ history()
      case "setDefault"     ⇒ setDefault()
      case "getDefault"     ⇒ getDefault
      case "resetDefault"   ⇒ resetDefault()
      case x ⇒
        throw new RuntimeException(s"Unknown subcommand: $x")
    }
  }
}
