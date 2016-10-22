package javacsw.services.ccs

import java.util.Optional

import akka.actor.{ActorRef, Props}
import csw.services.ccs.SequentialExecution
import csw.services.ccs.SequentialExecution.SequentialExecutor
import csw.util.config.Configurations.SetupConfigArg

import scala.compat.java8.OptionConverters._

/**
  * Enable Java access to SequentialExecution objects
  */
object JSequentialExecution {

  def props(sca: SetupConfigArg, commandOriginator: Optional[ActorRef]): Props = SequentialExecutor.props(sca, commandOriginator.asScala)

  def StopCurrentCommand = SequentialExecution.SequentialExecutor.StopCurrentCommand

  def CommandStart = SequentialExecution.SequentialExecutor.CommandStart
}
