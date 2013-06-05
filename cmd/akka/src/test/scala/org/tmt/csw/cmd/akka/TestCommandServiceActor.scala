package org.tmt.csw.cmd.akka

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import com.typesafe.config.{ConfigRenderOptions, Config, ConfigFactory}
import java.io.StringReader
import scala.concurrent.Await
import org.tmt.csw.cmd.{RunId, CommandServiceActor}


class TestComponent extends OmoaComponent {
  /**
   * The unique name of the component
   */
  def getName: String = "TestComponent"

  /**
   * A target OMOA component uses the Setup Config information to configure the target OMOA component.
   * The phrase used to describe this is a component must match the Config. In a recursive way, it can
   * either match the Config itself or pass all or part of the Config on to another OMOA component
   * (or create a new Config). To match a Setup Config, an SEC performs calculations or starts actions
   * in one or more Assemblies or HCDs. To pass it on, the SEC breaks the Config apart into new Configs
   * and Submits the new Configs to other SECs, Assemblies or HCDs and tracks their progress.
   */
  def matchConfig(config: Config) {
    val options = ConfigRenderOptions.defaults().setOriginComments(false).setComments(false).setJson(false).setFormatted(false)
    println("XXX TestComponent: matchConfig: " + config.root.render(options))
    for( a <- 1 to 3){
      Thread.sleep(1000)
      println("XXX Sleeping")
    }
  }

}

/**
 * Tests the Command Service actor
 */
class TestCommandServiceActor extends TestKit(ActorSystem("mySystem")) with ImplicitSender with FunSuite with BeforeAndAfterAll {
  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)
  implicit val dispatcher = system.dispatcher

  test("Test the CommandServiceActor") {

    // Create the actor
    val component = new TestComponent()
    val commandServiceActor = system.actorOf(Props(new CommandServiceActor(component)), name = "commandService")

    // Queue a command
    val config = ConfigFactory.parseReader(new StringReader(TestConfig.testConfig))
//    val runId = Await.result(commandServiceActor ?
//      CommandServiceActor.QueueSubmit(config),
//      duration).asInstanceOf[RunId]
//    println("XXX got runId: " + runId)

    val f = commandServiceActor ? CommandServiceActor.QueueSubmit(config)
    f onSuccess {
      case runId: RunId =>
        println("XXX got runId: " + runId)
        Thread.sleep(3000)
        commandServiceActor ! CommandServiceActor.QueueStop
        system.shutdown()
    }
    f onFailure {
      case e: Exception =>
        e.printStackTrace()
        system.shutdown()
    }

     // Wait for above to complete!
     system.awaitTermination()
  }

}
