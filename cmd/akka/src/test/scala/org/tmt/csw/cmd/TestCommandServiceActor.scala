package org.tmt.csw.cmd

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import com.typesafe.config.{Config, ConfigFactory}
import java.io.StringReader
import scala.concurrent.Await


class TestComponent extends OmoaComponent {
  /**
   * A target OMOA component uses the Setup Config information to configure the target OMOA component.
   * The phrase used to describe this is a component must match the Config. In a recursive way, it can
   * either match the Config itself or pass all or part of the Config on to another OMOA component
   * (or create a new Config). To match a Setup Config, an SEC performs calculations or starts actions
   * in one or more Assemblies or HCDs. To pass it on, the SEC breaks the Config apart into new Configs
   * and Submits the new Configs to other SECs, Assemblies or HCDs and tracks their progress.
   */
  def matchConfig(config: Config) {
    println("XXX TestComponent: matchConfig: " + config.toString)
  }
}

/**
 * Tests the Command Service actor
 */
class TestCommandServiceActor extends TestKit(ActorSystem("mySystem")) with ImplicitSender with FunSuite with BeforeAndAfterAll {
  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)

  test("Test the CommandServiceActor") {

    // Create the actor
    val commandServiceActor = system.actorOf(Props(new CommandServiceActor()), name = "commandService")

    // Queue a command
    val config = ConfigFactory.parseReader(new StringReader(TestSettings.testConfig))
    val component = new TestComponent()
    val runId = Await.result(commandServiceActor ?
      CommandServiceActor.QueueSubmit(component, config),
      duration).asInstanceOf[RunId]

    println("XXX got runId: " + runId)
  }

}
