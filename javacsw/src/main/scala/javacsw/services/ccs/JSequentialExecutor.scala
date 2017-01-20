package javacsw.services.ccs

import java.util.Optional

import akka.actor.{ActorRef, Props}
import csw.services.ccs.SequentialExecutor
import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}

import scala.compat.java8.OptionConverters._

/**
 * Enable Java access to SequentialExecutor object
 */
object JSequentialExecutor {

  def props(commandProcessor: ActorRef, sca: SetupConfigArg, commandOriginator: Optional[ActorRef]): Props =
    SequentialExecutor.props(commandProcessor, sca, commandOriginator.asScala)

  def ExecuteOne(sc: SetupConfig, commandOriginator: Optional[ActorRef]) = SequentialExecutor.ExecuteOne(sc, commandOriginator.asScala)
  def StopCurrentCommand = SequentialExecutor.StopCurrentCommand
  def CommandStart = SequentialExecutor.CommandStart
}
