package javacsw.services.pkg

import java.util.Optional

import com.typesafe.config.Config
import csw.services.pkg.Component.HcdInfo
import csw.services.pkg.ContainerComponent
import scala.compat.java8.OptionConverters._

/**
  * A Java API to the ContainerComponent scala class.
  */
object JContainerComponent {

  def parseHcd(name: String, conf: Config): Optional[HcdInfo] = {
    ContainerComponent.parseHcd(name, conf).asJava
  }
}

