package org.tmt.csw.test.app

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import org.tmt.csw.cs.api.{ConfigId, ConfigData}
import org.tmt.csw.cs.core.ConfigString
import org.tmt.csw.cs.akka.ConfigServiceActor._

object TestActor {
  def props(configServiceActor: ActorRef) = Props(classOf[TestActor], configServiceActor)
}


// A test actor used to send messages to the config service
class TestActor(configServiceActor: ActorRef) extends Actor with ActorLogging {

  val configFileName = "testApp/config.conf"
  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)

  def receive = {
    case Start =>
      val future = configServiceActor ? GetRequest(configFileName)
      future onSuccess {
        case Some(configData) => readConfigFile(configData.asInstanceOf[ConfigData])
        case None => createNewConfigFile()
        case x => log.info(s"unexpected result: $x")
      }
      future onFailure {
        case e: Exception => {
          log.error(e, "Get got exception")
        }
      }
  }

  def readConfigFile(configData: ConfigData) {
    log.info(s"Get => $configData")
  }

  def createNewConfigFile() {
    log.info("Get => None: make new config file")
    val configData = new ConfigString("# TestApp Settings\n\nkey1 = value1\nkey2 = value2\n")
    val future = configServiceActor ? CreateRequest(configFileName, configData)
    future onSuccess {
      case id: ConfigId =>
        log.info("created new config file")
        self ! Start
      case x => log.info(s"unexpected result: $x")
    }
    future onFailure {
      case e: Exception => {
        log.error(e, "Create got exception")
      }
    }
  }
}
