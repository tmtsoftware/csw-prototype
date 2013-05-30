package org.tmt.csw.cmd

import org.scalatest.FunSuite
import com.typesafe.config.ConfigFactory
import java.io.StringReader

object TestSettings {
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
class TestSettings extends FunSuite {


  test("Test using the Akka Config classes to parse a config from a string") {
    // A test config file taken from the Akka docs

    val config = ConfigFactory.parseReader(new StringReader(TestSettings.testConfig))
    assert(config.getStringList("akka.loggers").get(0) == "akka.event.slf4j.Slf4jLogger")
    assert(config.getString("akka.loglevel") == "DEBUG")
    assert(config.getInt("akka.actor.default-dispatcher.throughput") == 10)
  }
}
