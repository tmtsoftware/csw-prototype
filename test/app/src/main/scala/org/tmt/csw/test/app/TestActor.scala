package org.tmt.csw.test.app

import akka.actor.Actor
import akka.util.Timeout
import scala.concurrent.duration._
import org.tmt.csw.cs.akka.{CreateResult, CreateRequest, GetResult, GetRequest}
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global
import scala.Option
import org.tmt.csw.cs.api.ConfigData
import org.tmt.csw.cs.core.ConfigString

// A test actor used to send messages to the config service
class TestActor extends Actor {

  val configFileName = "testApp/config.conf"
  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)

  def receive = {
    case Start =>
      val future = ActorFactory.configServiceActor ? GetRequest(configFileName)
      future.onSuccess {
//        case GetResult(Some(configData)) => readConfigFile(configData)
//        case GetResult(None) => createNewConfigFile()
          case GetResult(opt) => readConfigFile(opt)
        case _ => println("XXX unexpected result?")
      }
      future.onFailure {
        case e: Exception => {
          println("XXX Get got exception: " + e)
          e.printStackTrace()
        }
      }
  }

  def readConfigFile(configData: Option[ConfigData]) {
    println("XXX Get => " + configData)
    configData match {
      case Some(configData) => readConfigFile(configData)
      case None => createNewConfigFile()
    }
  }

  def readConfigFile(configData: ConfigData) {
    println("XXX Get => " + configData)
  }

  def createNewConfigFile() {
    println("XXX Get => None: make new config file")
    val configData = new ConfigString("# TestApp Settings\n\nkey1 = value1\nkey2 = value2\n")
    val future = ActorFactory.configServiceActor ? CreateRequest(configFileName, configData)
    future.onSuccess {
      case CreateResult(id) => println("XXX created new config file")
      case _ => println("XXX unexpected result?")
    }
    future.onFailure {
      case e: Exception => {
        println("XXX Create got exception: " + e)
        e.printStackTrace()
      }
    }
  }
}
