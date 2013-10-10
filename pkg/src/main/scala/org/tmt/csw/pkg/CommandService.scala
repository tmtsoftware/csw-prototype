package org.tmt.csw.pkg

import akka.actor._
import org.tmt.csw.cmd.akka.{CommandStatus, CommandServiceMessage, ConfigMessage, CommandServiceActor}

trait CommandService {
  this: Actor with ActorLogging =>
  def name: String

  def commandServiceActor: ActorRef = context.actorOf(Props[CommandServiceActor], name = name)

  // Receive command service messages
  def receiveCommands: Receive = {
    case m: ConfigMessage => commandServiceActor ! m
    case m: CommandServiceMessage => commandServiceActor ! m
    case status: CommandStatus => receivedCommandStatus(status)
  }

  /**
   * Called with the status for a previously submitted command
   * @param status the command status
   */
  def receivedCommandStatus(status: CommandStatus): Unit = {
    log.info(s"received command status: $status")
  }
}

