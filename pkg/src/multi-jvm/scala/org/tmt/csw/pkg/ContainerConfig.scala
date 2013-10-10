package scala.org.tmt.csw.pkg

import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

object ContainerConfig extends MultiNodeConfig {

  val container1 = role("container1")
  val container2 = role("container2")

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
      | """.stripMargin
  commonConfig(ConfigFactory.parseString(akkaConfig))
}
