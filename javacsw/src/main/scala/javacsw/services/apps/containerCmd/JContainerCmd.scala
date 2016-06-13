package javacsw.services.apps.containerCmd

import java.util.Optional
import scala.compat.java8.OptionConverters._

import csw.services.apps.containerCmd.ContainerCmd

/**
 * Java API to ContainerCmd, used to create CSW container
 */
object JContainerCmd {
  def createContainerCmd(name: String, args: Array[String], resource: Optional[String]): ContainerCmd = {
    ContainerCmd(name, args, resource.asScala)
  }
}
