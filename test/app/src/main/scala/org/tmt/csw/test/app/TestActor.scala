package org.tmt.csw.test.app

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import org.tmt.csw.cs.api.{ConfigId, ConfigData}
import org.tmt.csw.cs.core.ConfigString
import org.tmt.csw.cs.akka.ConfigServiceActor._
import org.tmt.csw.cmd.akka.{AssemblyCommandServiceActor, OneAtATimeCommandQueueController, CommandStatus}
import org.tmt.csw.cmd.core.Configuration
import java.io.File
import org.tmt.csw.cmd.akka.CommandServiceActor.Submit

class TestAssemblyCommandServiceActor extends AssemblyCommandServiceActor with OneAtATimeCommandQueueController {
  override def receive: Receive = receiveCommands
}

object TestActor {
  def props(configServiceActor: ActorRef): Props = Props(classOf[TestActor], configServiceActor)
}

// A test actor used to send messages to the config service
class TestActor(configServiceActor: ActorRef) extends Actor with ActorLogging {

  val configFileName = new File("testApp/config.conf")
  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)
  val commandServiceActor = context.actorOf(Props[TestAssemblyCommandServiceActor], name = s"testAppCommandServiceActor")

  override def receive: Receive = {
    case "Start" =>
      val future = configServiceActor ? GetRequest(configFileName)
      future onSuccess {
        case Some(configData) => readConfigFile(configData.asInstanceOf[ConfigData])
        case None => createNewConfigFile()
        case x => log.info(s"unexpected result from $sender: $x")
      }
      future onFailure {
        case e: Exception => {
          log.error(e, "Get got exception")
        }
      }

    case status: CommandStatus =>
      log.info(s"Received command status: $status")
      if (status.done) {
//        context.stop(self)
        context.system.shutdown()
      }

    case x => log.debug(s"Received unknown message")
  }

  def readConfigFile(configData: ConfigData): Unit = {
    log.info(s"Get => $configData")

    // XXX could create an actor on the remote system also, but then the jar for it needs to be in the classpath
//    val configActor = context.actorOf(TestConfigActor.props("config.tmt.tel.base.pos"), name = s"TestConfigActorRemote")


    // Submit a configuration to the command service
    commandServiceActor ! Submit(Configuration(configData.getBytes))
  }

  def createNewConfigFile(): Unit = {
    log.info("Get => None: make new config file")
    val testConfig =
      """
        |      config {
        |        info {
        |          obsId = TMT-2021A-C-2-1
        |        }
        |        tmt.tel.base.pos {
        |          posName = NGC738B
        |          c1 = "22:35:58.530"
        |          c2 = "33:57:55.40"
        |          equinox = J2000
        |        }
        |        tmt.tel.ao.pos.one {
        |          c1 = "22:356:01.066"
        |          c2 = "33:58:21.69"
        |          equinox = J2000
        |        }
        |      }
        |
      """.stripMargin
    val configData = new ConfigString(testConfig)
    val future = configServiceActor ? CreateRequest(configFileName, configData)
    future onSuccess {
      case id: ConfigId =>
        log.info("created new config file")
        self ! "Start"
      case x => log.info(s"unexpected result: $x")
    }
    future onFailure {
      case e: Exception => {
        log.error(e, "Create got exception")
      }
    }
  }
}
