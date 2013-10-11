package org.tmt.csw.pkg

import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

object ContainerConfig extends MultiNodeConfig {

  val container1 = role("container1")
  val container2 = role("container2")

  // XXX Get this to work from a file
  val akkaConfig =
    """
      |akka {
      |    loglevel = "INFO"
      |    remote {
      |        enabled-transports = ["akka.remote.netty.tcp"]
      |        transport = "akka.remote.netty.NettyRemoteTransport"
      |        netty.tcp {
      |            hostname = "127.0.0.1"
      |        }
      |    }
      |}
      |
      |csw {
      |    test {
      |        // The URL of the main GIT repository used by the Config Service
      |        test-main-repository = "file:///tmp/csapptest"
      |
      |        // Path to the local GIT repository, which will sync with the main Git repository
      |        test-local-repository = "/tmp/csapptestlocal"
      |
      |        // The REST interface to the Assembly (command service) in Container-1
      |        assembly1-http {
      |            interface = localhost
      |            port      = 8089
      |            timeout   = 10 seconds
      |        }
      |
      |        // URIs for accessing two remote HCDs (TODO: Get this from location service)
      |        hcd2a = "akka.tcp://HCD-2A@127.0.0.1:9992/user/HCD-2A"
      |        hcd2b = "akka.tcp://HCD-2B@127.0.0.1:9993/user/HCD-2B"
      |    }
      |}
      | """.stripMargin
  commonConfig(ConfigFactory.parseString(akkaConfig))
}
