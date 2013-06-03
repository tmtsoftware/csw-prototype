package org.tmt.csw.cmd

import org.scalatest.FunSuite
import com.typesafe.config.{ConfigRenderOptions, ConfigFactory}
import java.io.StringReader
import scala.collection.JavaConverters._

object TestConfig {
  val testConfig =
    """
      |    # In this file you can override any option defined in the reference files.
      |    # Copy in parts of the reference files and modify as you please.
      |
      |    akka {
      |
      |    # Loggers to register at boot time (akka.event.Logging$DefaultLogger logs
      |    # to STDOUT)
      |    loggers = ["akka.event.slf4j.Slf4jLogger"]
      |
      |    # Log level used by the configured loggers (see "loggers") as soon
      |    # as they have been started; before that, see "stdout-loglevel"
      |    # Options: OFF, ERROR, WARNING, INFO, DEBUG
      |    loglevel = "DEBUG"
      |
      |    # Log level for the very basic logger activated during AkkaApplication startup
      |    # Options: OFF, ERROR, WARNING, INFO, DEBUG
      |    stdout-loglevel = "DEBUG"
      |
      |    actor {
      |    default-dispatcher {
      |    # Throughput for default Dispatcher, set to 1 for as fair as possible
      |    throughput = 10
      |    }
      |    }
      |
      |    remote {
      |    server {
      |    # The port clients should connect to. Default is 2552 (AKKA)
      |    port = 2562
      |    }
      |    }
      |    }
    """.stripMargin
}

/**
 * Test the Confog object
  */
class TestConfig extends FunSuite {


  test("Test using the Akka Config classes to parse a config from a string") {
    // A test config file taken from the Akka docs

    val config = ConfigFactory.parseReader(new StringReader(TestConfig.testConfig))
    assert(config.getStringList("akka.loggers").get(0) == "akka.event.slf4j.Slf4jLogger")
    assert(config.getString("akka.loglevel") == "DEBUG")
    assert(config.getInt("akka.actor.default-dispatcher.throughput") == 10)
  }



  test("Test creating a config in code") {
    val simplePathMapValue = Map("x.y" -> 4, "z" -> 5).asInstanceOf[Map[String, AnyRef]].asJava
    val pathMapValue = Map("a.c" -> 1, "b" -> simplePathMapValue).asInstanceOf[Map[String, AnyRef]].asJava

    val conf = ConfigFactory.parseMap(pathMapValue)

    assert(2 == conf.root.size)
    assert(4 == conf.getInt("b.x.y"))
    assert(5 == conf.getInt("b.z"))
    assert(1 == conf.getInt("a.c"))

    val options = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
    assert("b{z=5,x{y=4}},a{c=1}" == conf.root.render(options))
    assert("z=5,x{y=4}" == conf.root.get("b").render(options))
  }
}
