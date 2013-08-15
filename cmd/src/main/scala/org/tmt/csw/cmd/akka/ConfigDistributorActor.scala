package org.tmt.csw.cmd.akka

import akka.actor.{Terminated, ActorRef, ActorLogging, Actor}
import org.tmt.csw.cmd.akka.CommandServiceMessage._
import org.tmt.csw.cmd.akka.ConfigDistributorActor._

sealed trait ConfigDistributorMessage

/**
 * Defines the message types used by this actor.
 */
object ConfigDistributorActor {

  /**
   * Message used by a config actor to register interest in the given set of config paths.
   * @param configPaths a set of dot separated path expressions, each referring to a hierarchy in a Configuration object
   * @param actorRef a reference to the sender (not the same as the implicit sender if 'ask' is used)
   */
  case class Register(configPaths: Set[String], actorRef: ActorRef) extends ConfigDistributorMessage

  /**
   * Reply sent when registration is complete
   */
  case class Registered() extends ConfigDistributorMessage
}


/**
 * This actor receives configurations from the CommandServiceActor and sends
 * parts of them on to config actors who have registered for them.
 */
class ConfigDistributorActor extends Actor with ActorLogging {

  // Describes a config path and the actor that registered to handle it
  case class RegistryEntry(path: String, actorRef: ActorRef)

  // Set of config paths and the actors that registered to handle them
  private var registry = Set[RegistryEntry]()

  // Combines a Submit object with a reference to the target actor
  case class SubmitInfo(submit: SubmitWithRunId, target: ActorRef)

  // Maps runId to list of submitted config parts sent to the registered config actors
  case class ConfigPartInfo(configParts: List[SubmitInfo], configState: ConfigState, originalSubmit: SubmitWithRunId)

  private var configParts = Map[RunId, ConfigPartInfo]()

  // Maps the RunId for a config part to the RunId for the complete config
  private var runIdMap = Map[RunId, RunId]()

  /**
   * Messages received in the normal state.
   */
  def receive = {
    case Register(configPaths, actorRef) => register(configPaths, actorRef)
    case s: SubmitWithRunId => submit(s)
    case ConfigCancel(runId) => cancel(runId)
    case ConfigAbort(runId) => abort(runId)
    case ConfigPause(runId) => pause(runId)
    case ConfigResume(runId) => resume(runId)

    // Status Results for a config part from a ConfigActor
    case state: ConfigState => checkIfDone(state)

    case Terminated(actorRef) => unregister(actorRef)

    case x => log.error(s"Unexpected ConfigActor message: $x")
  }

  def register(configPaths: Set[String], actorRef: ActorRef): Unit = {
    configPaths.foreach(path => registry += RegistryEntry(path, actorRef))
    sender ! Registered()

    // Add a listener in case the actor dies?
    context.watch(actorRef)
  }

  def unregister(actorRef: ActorRef): Unit = {
    registry = registry.filterNot(entry => entry.actorRef == actorRef)
    /*
    registry.toList.foreach(entry =>
      if (entry.actorRef == actorRef) {
        registry -= entry
        log.info(s"Actor $actorRef died: Unregistering it for ${entry.path}")
      })
      */
  }

  /**
   * Called when a config is submitted.
   */
  private def submit(submit: SubmitWithRunId): Unit = {
    // Send each actor that registered for a config path that part of the config, if found,
    // and save a list so we can check if all are done later when the status messages are received.
    val submitInfoList = registry.collect {
      case registryEntry =>
        log.debug(s"submit: checking registry entry $registryEntry")
        submit.config.hasPath(registryEntry.path) match {
          case true =>
            // Give each config part a unique runid, so we can identify it later when the status is received
            val runIdPart = RunId()
            val submitPart = SubmitWithRunId(submit.config.getConfig(registryEntry.path), self, runIdPart)
            runIdMap += (runIdPart -> submit.runId)
            log.debug(s"Sending config part to ${registryEntry.actorRef}")
            registryEntry.actorRef ! submitPart
            Some(SubmitInfo(submitPart, registryEntry.actorRef))
          case false => None
        }
    }.flatten.toList
    // Add info to map indexed by runId is so we can determine when all parts are done and reply to the original sender
    configParts += (submit.runId -> ConfigPartInfo(submitInfoList, ConfigState.Submitted(submit.runId), submit))
  }

  /**
   * Called when a status message is received from a config actor.
   * If all of the config parts are done, send the final status to the original sender.
   * @param configState the status of the config part from the worker actor
   */
  def checkIfDone(configState: ConfigState): Unit = {
    log.debug(s"Check if done: state = $configState")
    if (configState.done()) {
      runIdMap.get(configState.runId()) match {
        case Some(runId) =>
          runIdMap -= configState.runId()
          checkIfDone(configState, runId)
        case None =>
          log.error(s"RunId for config part ${configState.runId()} not found")
      }
    } else {
      // Received other state for part: one of (Submitted, Paused, Resumed)
      // XXX?
    }
  }

  /**
   * Called when a status message is received from a config actor.
   * If all of the config parts are done, send the final status to the original sender.
   * @param configState the status of the config part from the worker actor
   * @param runId the RunId of the original (complete) config
   */
  def checkIfDone(configState: ConfigState, runId: RunId): Unit = {
    configParts.get(runId) match {
      case Some(configPartInfo) =>
        val newConfigState = getConfigState(configState, configPartInfo)
        val remainingParts = configPartInfo.configParts.filter(_.submit.runId != configState.runId())
        if (remainingParts.isEmpty) {
          // done, return status to sender
          configParts -= runId
          log.debug(s"All config parts done: Returning $newConfigState to ${configPartInfo.originalSubmit.submitter}")
          configPartInfo.originalSubmit.submitter ! returnStatus(newConfigState, runId)
        } else {
          // one part is done, some still remaining: Update the map to remove the part that is done and update the status
          log.debug(s"${remainingParts.length} parts left for runId $runId")
          configParts += (runId -> ConfigPartInfo(remainingParts, newConfigState, configPartInfo.originalSubmit))
        }

      case None =>
        log.error(s"Received status message for unknown runId: $runId")
    }
  }

  /**
   * Returns a ConfigState with the runId for the original submit.
   * If the config was canceled or aborted,
   */
  def getConfigState(configState: ConfigState, info: ConfigPartInfo): ConfigState = {
    info.configState match {
      case ConfigState.Canceled(runId) => info.configState
      case ConfigState.Aborted(runId) => info.configState
      case _ => configState.withRunId(info.originalSubmit.runId)
    }
  }

  // Returns a CommandStatus for the given ConfigState
  def returnStatus(state: ConfigState, runId: RunId): CommandStatus = {
    log.debug(s"Return status: $state")
    state match {
      case s: ConfigState.Completed => CommandStatus.Complete(runId)
      case s: ConfigState.Canceled => CommandStatus.Canceled(runId)
      case s: ConfigState.Aborted => CommandStatus.Aborted(runId)
      case x => CommandStatus.Error(runId, new RuntimeException(s"Unexpected message: $x"))
    }
  }


  /**
   * Called when a config is paused.
   */
  def pause(runId: RunId): Unit = {
    configParts.get(runId) match {
      case Some(configPartInfo) =>
        configPartInfo.configParts.foreach {
          part =>
            part.target ! ConfigPause(part.submit.runId)
        }
      case None =>
        log.error(s"Received pause config command for unknown runId: $runId")
    }

  }

  /**
   * Called when a config is resumed.
   */
  def resume(runId: RunId) : Unit = {
    configParts.get(runId) match {
      case Some(configPartInfo) =>
        configPartInfo.configParts.foreach {
          part =>
            part.target ! ConfigResume(part.submit.runId)
        }
      case None =>
        log.error(s"Received resume config command for unknown runId: $runId")
    }

  }

  def doComm(x:ActorRef, c:ConfigState) = x ! c
  /**
   * Called when the config is canceled.
   */
  def cancel(runId: RunId) : Unit = {
    changeConfigState(runId, ConfigState.Canceled(runId), "cancel")

    configParts.get(runId) match {
      case Some(configPartInfo) =>
        configPartInfo.configParts.map(x => doComm(x.target, ConfigState.Canceled(runId)))
      /*
        configPartInfo.configParts.foreach {
          part =>
            part.target ! ConfigCancel(part.submit.runId)
        }
        */
      case None =>
        log.error(s"Received cancel config command for unknown runId: $runId")
    }
  }

  /**
   * Called when the config is aborted.
   */
  def abort(runId: RunId) : Unit = {
    changeConfigState(runId, ConfigState.Aborted(runId), "abort")

    configParts.get(runId) match {
      case Some(configPartInfo) =>
        configPartInfo.configParts.foreach {
          part =>
            part.target ! ConfigAbort(part.submit.runId)
        }
      case None =>
        log.error(s"Received abort config command for unknown runId: $runId")
    }
  }

  /**
   * Changes the ConfigState for the given runId
   */
  def changeConfigState(runId: RunId, newConfigState: ConfigState, logName: String): Unit = {
    configParts.get(runId) match {
      case Some(configPartInfo) =>
        configParts += (runId -> ConfigPartInfo(configPartInfo.configParts, newConfigState, configPartInfo.originalSubmit))
      case None =>
        log.error(s"Received $logName config command for unknown runId: $runId")
    }
  }
}
