package org.tmt.csw.cmd.akka

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import scala.Some

object ConfigDistributorActor {
  /**
   * Used to create this actor.
   * @param commandStatusActor reference to the command status actor, which receives the final status of commands
   */
  def props(commandStatusActor: ActorRef): Props = Props(classOf[ConfigDistributorActor], commandStatusActor)
}

/**
 * This actor receives configurations and sends parts of them on to actors who have registered for them.
 * @param commandStatusActor reference to the command status actor, which receives the final status of commands
 */
class ConfigDistributorActor(commandStatusActor: ActorRef) extends Actor with ActorLogging {

  import ConfigRegistrationActor._
  import CommandQueueActor._
  import ConfigActor._

  // Combines a Submit object with a reference to the target actor
  private case class SubmitInfo(submit: SubmitWithRunId, target: ActorRef)

  // Maps runId to list of submitted config parts sent to the registered config actors
  private case class ConfigPartInfo(configParts: List[SubmitInfo], commandStatus: CommandStatus, originalSubmit: SubmitWithRunId)

  // Used to determine when all config parts are done and reply to the original sender
  private var configParts = Map[RunId, ConfigPartInfo]()

  // Maps the RunId for a config part to the RunId for the complete config
  private var runIdMap = Map[RunId, RunId]()

  // Set of registry entries for actors that process configurations
  private var registry = Set[RegistryEntry]()

  /**
   * Messages received in the normal state.
   */
  override def receive: Receive = {
    case RegistryUpdate(reg) => registry = reg
    case QueueWorkAvailable => queueWorkAvailable()
    case s: SubmitWithRunId => submit(s)
    case ConfigCancel(runId) => cancel(runId)
    case ConfigAbort(runId) => abort(runId)
    case ConfigPause(runId) => pause(runId)
    case ConfigResume(runId) => resume(runId)

    // Status Results for a config part from a ConfigActor
    case status: CommandStatus => checkIfDone(status)

    case x => log.error(s"Unexpected ConfigDistributorActor message from $sender: $x")
  }


  // Tell all the registered actors that there is work available
  private def queueWorkAvailable(): Unit = {
    registry.foreach(_.actorRef ! QueueWorkAvailable)
  }

  /**
   * Called when a config is submitted.
   * Send each actor that registered for a config path that part of the config, if found,
   * and save a list so we can check if all are done later when the status messages are received.
   */
  private def submit(submit: SubmitWithRunId): Unit = {
    // First get a list of the config parts we need to send and the target actors that should get them
    val submitInfoList = registry.map {
      registryEntry => getSubmitInfo(submit, registryEntry)
    }.flatten.toList

    // Add info to map indexed by runId is so we can determine when all parts are done and reply to the original sender
    configParts += (submit.runId -> ConfigPartInfo(submitInfoList, CommandStatus.Submitted(submit.runId), submit))

    // Keep a map of the runIds for later reference
    runIdMap ++= submitInfoList.map {
      submitInfo => (submitInfo.submit.runId, submit.runId)
    }.toMap

    // Send the submit messages to the target actors
    submitInfoList.foreach {
      submitInfo =>
        log.debug(s"Sending config part to ${submitInfo.target}")
        submitInfo.target ! submitInfo.submit
    }
  }

  // Returns Some(SubmitInfo) if there is a matching path in the config to be submitted, otherwise None.
  private def getSubmitInfo(submit: SubmitWithRunId, registryEntry: RegistryEntry): Option[SubmitInfo] = {
    log.debug(s"submit: checking registry entry $registryEntry")
    submit.config.hasPath(registryEntry.path) match {
      case true =>
        // Give each config part a unique runid, so we can identify it later when the status is received
        val runIdPart = RunId()
        val submitPart = SubmitWithRunId(submit.config.getConfig(registryEntry.path), self, runIdPart)
        Some(SubmitInfo(submitPart, registryEntry.actorRef))
      case false => None
    }
  }

  /**
   * Called when a status message is received from a config actor.
   * @param commandStatus the status of the config part from the worker actor
   */
  private def checkIfDone(commandStatus: CommandStatus): Unit = {
    log.debug(s"Check if done: state = $commandStatus")
    if (commandStatus.done) {
      val runIdOpt = runIdMap.get(commandStatus.runId)
      runIdOpt.fold(log.error(s"RunId for config part ${commandStatus.runId} not found")) {
        runIdMap -= commandStatus.runId
        checkIfDone(commandStatus, _)
      }
    }
    //    else {
    //      // Received other state for part: one of (Submitted, Paused, Resumed)
    //      // XXX?
    //    }
  }

  /**
   * Called when a status message is received from a config actor.
   * @param commandStatus the status of the config part from the worker actor
   * @param runId the RunId of the original (complete) config
   */
  private def checkIfDone(commandStatus: CommandStatus, runId: RunId): Unit = {
    configParts.get(runId).fold(log.error(s"Received status message for unknown runId: $runId")) {
      checkIfDone(commandStatus, runId, _)
    }
  }

  /**
   * If all of the config parts are done, send the final status to the original sender.
   * @param commandStatus the status of the config part from the worker actor
   * @param runId the RunId of the original (complete) config
   * @param configPartInfo contains list of submitted config parts sent to the registered config actors
   */
  private def checkIfDone(commandStatus: CommandStatus, runId: RunId, configPartInfo: ConfigPartInfo): Unit = {
    val newCommandStatus = getCommandStatus(commandStatus, configPartInfo)
    val remainingParts = configPartInfo.configParts.filter(_.submit.runId != commandStatus.runId)
    if (remainingParts.isEmpty) {
      // done, return status to sender
      configParts -= runId
      log.debug(s"All config parts done: Returning $newCommandStatus for submitter ${configPartInfo.originalSubmit.submitter}")
      val status = returnStatus(newCommandStatus, runId)
      val submitter = configPartInfo.originalSubmit.submitter
      commandStatusActor ! CommandStatusActor.StatusUpdate(status, submitter)
    } else {
      // one part is done, some still remaining: Update the map to remove the part that is done and update the status
      log.debug(s"${remainingParts.length} parts left for runId $runId")
      configParts += (runId -> ConfigPartInfo(remainingParts, newCommandStatus, configPartInfo.originalSubmit))
    }
  }

  /**
   * Returns a CommandStatus with the runId for the original submit.
   * If the config was canceled or aborted,
   * XXX TODO FIXME
   */
  private def getCommandStatus(commandStatus: CommandStatus, info: ConfigPartInfo): CommandStatus = {
    info.commandStatus match {
      case CommandStatus.Canceled(runId) => info.commandStatus
      case CommandStatus.Aborted(runId) => info.commandStatus
      case _ => commandStatus.withRunId(info.originalSubmit.runId)
    }
  }

  // Returns a CommandStatus for the given CommandStatus
  // XXX TODO FIXME
  private def returnStatus(state: CommandStatus, runId: RunId): CommandStatus = {
    log.debug(s"Return status: $state")
    state match {
      case s: CommandStatus.Completed => CommandStatus.Completed(runId)
      case s: CommandStatus.Canceled => CommandStatus.Canceled(runId)
      case s: CommandStatus.Aborted => CommandStatus.Aborted(runId)
      case x => CommandStatus.Error(runId, s"Unexpected command status: $x")
    }
  }


  /**
   * Called when a config is paused.
   */
  private def pause(runId: RunId): Unit = {
    configParts.get(runId).fold(log.error(s"Received pause config command for unknown runId: $runId")) {
      _.configParts.foreach {
        part => part.target ! ConfigPause(part.submit.runId)
      }
    }
  }

  /**
   * Called when a config is resumed.
   */
  private def resume(runId: RunId): Unit = {
    configParts.get(runId).fold(log.error(s"Received resume config command for unknown runId: $runId")) {
      _.configParts.foreach {
        part => part.target ! ConfigResume(part.submit.runId)
      }
    }
  }

  /**
   * Called when the config is canceled.
   */
  private def cancel(runId: RunId): Unit = {
    changeCommandStatus(runId, CommandStatus.Canceled(runId), "cancel")
    configParts.get(runId).fold(log.error(s"Received cancel config command for unknown runId: $runId")) {
      _.configParts.foreach {
        part => part.target ! ConfigCancel(part.submit.runId)
      }
    }
  }

  /**
   * Called when the config is aborted.
   */
  private def abort(runId: RunId): Unit = {
    changeCommandStatus(runId, CommandStatus.Aborted(runId), "abort")
    configParts.get(runId).fold(log.error(s"Received abort config command for unknown runId: $runId")) {
      _.configParts.foreach {
        part => part.target ! ConfigAbort(part.submit.runId)
      }
    }
  }

  /**
   * Changes the CommandStatus for the given runId
   */
  private def changeCommandStatus(runId: RunId, newCommandStatus: CommandStatus, logName: String): Unit = {
    configParts.get(runId).fold(log.error(s"Received $logName config command for unknown runId: $runId")) {
      configPartInfo =>
        configParts += (runId -> ConfigPartInfo(configPartInfo.configParts, newCommandStatus, configPartInfo.originalSubmit))
    }
  }
}
