package javacsw.services.ccs

import java.util.Optional

import akka.actor.{ActorRef, Props}
import csw.services.ccs.SequentialExecutor
import csw.util.config.Configurations.SetupConfigArg

import scala.compat.java8.OptionConverters._

/**
 * Enable Java access to SequentialExecution objects
 */
object JSequentialExecution {

  def props(sca: SetupConfigArg, commandOriginator: Optional[ActorRef]): Props = SequentialExecutor.props(sca, commandOriginator.asScala)

  def StopCurrentCommand = SequentialExecutor.StopCurrentCommand

  def CommandStart = SequentialExecutor.CommandStart
}
