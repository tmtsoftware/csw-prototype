package javacsw.services.apps.containerCmd

import csw.services.apps.containerCmd.ContainerCmd
import collection.JavaConverters._

/**
 * Java API to ContainerCmd, used to create CSW container
 */
object JContainerCmd {
  def createContainerCmd(name: String, args: Array[String], resources: java.util.Map[String, String]): ContainerCmd = {
    ContainerCmd(name, args, resources.asScala.toMap)
  }
}
