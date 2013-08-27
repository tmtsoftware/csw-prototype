package org.tmt.csw.cs.spray

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRefFactory, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import java.io.File
import org.tmt.csw.cs.akka.{ConfigServiceActor, TestRepo}
import spray.routing.HttpService
import spray.testkit.Specs2RouteTest
import org.specs2.mutable.Specification
import spray.http.HttpResponse
import org.tmt.csw.cs.api.ConfigId

/**
 * Tests the ConfigService class
 */
class TestConfigService extends Specification with Specs2RouteTest with DefaultJsonFormats {
  import scala.concurrent.duration._
  import akka.util.Timeout

  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  implicit val timeout = Timeout(5000) // ms

  //  def actorRefFactory: ActorRefFactory = system

  // create a test repository and use it to create the actor and then the config HTTP service
  val manager = TestRepo.getConfigManager("test1")
  val configServiceActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")
  val configService = ConfigService(configServiceActor)
  val configServiceRoute = configService.route

  "The service" should {

    "return a greeting for GET requests to the root path" in {
      Post("/create") ~> configServiceRoute ~> check {
        entityAs[ConfigId] != null
      }
    }
  }
}
