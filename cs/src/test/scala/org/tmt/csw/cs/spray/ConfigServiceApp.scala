package org.tmt.csw.cs.spray

import akka.actor.{Props, ActorSystem}
import akka.io.IO
import spray.can.Http
import org.tmt.csw.cs.akka.{ConfigServiceActor, TestRepo}
import scala.concurrent.Await
import org.tmt.csw.cs.akka.ConfigServiceActor.CreateRequest
import org.tmt.csw.cs.core.ConfigString
import org.tmt.csw.cs.api.ConfigId
import akka.util.Timeout
import scala.concurrent.duration._
import java.io.File
import akka.pattern.ask

/**
 * Standalone app for config service (for test)
 */
object ConfigServiceApp extends App {

  val path1 = new File("some/test1/TestConfig1")
  val path2 = new File("some/test2/TestConfig2")

  val contents1 = "Contents of some file...\n"
  val contents2 = "New contents of some file...\n"
  val contents3 = "Even newer contents of some file...\n"

  val comment1 = "create comment"
  val comment2 = "update 1 comment"
  val comment3 = "update 2 comment"

  /**
   * Construct the ActorSystem we will use in our application
   */
  implicit lazy val system = ActorSystem("akka-spray")
  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)

  /**
   * Ensure that the constructed ActorSystem is shut down when the JVM shuts down
   */
  sys.addShutdownHook(system.shutdown())

  // create a test repository and use it to create the actor and then the config HTTP service
  val manager = TestRepo.getConfigManager("test1")
  val configServiceActor = system.actorOf(ConfigServiceActor.props(manager), name = "configService")

  // XXX FIXME: create a file to start
  val createId1 = Await.result(configServiceActor ?
    CreateRequest(path1, new ConfigString(contents1), comment1),
    duration).asInstanceOf[ConfigId]
  println(s"XXX Created file $path1 with id $createId1")
  // XXX FIXME

  val configService = ConfigService(configServiceActor)(system.dispatcher)
  val configServiceRoute = configService.route
  val rootService = system.actorOf(Props(new RoutedHttpService(configServiceRoute)))

  IO(Http)(system) ! Http.Bind(rootService, "0.0.0.0", port = 8080)
}
