package org.tmt.csw.cmd.akka

import akka.actor._
import org.tmt.csw.cmd.core.Configuration
import akka.pattern.ask
import scala.concurrent.Future
import akka.util.Timeout
import scala.concurrent.duration._
import org.tmt.csw.cmd.akka.ConfigActor._
import org.tmt.csw.cmd.akka.QueryWorkerActor.QueryInfo
import org.tmt.csw.ls.LocationServiceActor.ServicesReady
import scala.util.Failure
import org.tmt.csw.cmd.akka.CommandQueueActor.SubmitWithRunId
import scala.Some
import org.tmt.csw.cmd.akka.ConfigActor.ConfigResponse
import scala.util.Success
import org.tmt.csw.cmd.akka.ConfigDistributorActor.SubmitInfo
import org.tmt.csw.ls.LocationServiceActor.LocationServiceInfo
import org.tmt.csw.ls.LocationService

object ConfigDistributorActor {
  /**
   * Used to create this actor.
   * @param commandStatusActor reference to the command status actor, which receives the final status of commands
   */
  def props(commandStatusActor: ActorRef): Props = Props(classOf[ConfigDistributorActor], commandStatusActor)

  /**
   * Combines a Submit object with a reference to the target actor
   * @param path optional path in the config that the target actor is interested in (default: everything)
   * @param submit contains the config and runId
   * @param target the target actor receiving the config
   */
  case class SubmitInfo(path: Option[String], submit: SubmitWithRunId, target: ActorRef)
}

/**
 * This actor receives configurations and sends parts of them on to actors who have registered for them.
 * @param commandStatusActor reference to the command status actor, which receives the final status of commands
 */
class ConfigDistributorActor(commandStatusActor: ActorRef) extends Actor with ActorLogging with Stash {

  import ConfigActor._
  import ConfigDistributorActor._

  // Maps runId to the submit worker actor that is handling the submit
  var workers = Map[RunId, ActorRef]()

  // Start out in the waiting state
  override def receive: Receive = waitingForServices

  // Initial state until we get a list of running services to use as target actors
  def waitingForServices: Receive = {
    case ServicesReady(services: List[LocationServiceInfo]) =>
      log.info(s"All services ready: $services")
      val targetActors = for (service <- services if service.actorRefOpt.isDefined) yield service.actorRefOpt.get
      if (targetActors.size == services.size) {
        for(a <- targetActors) context.watch(a)
        log.info(s"Setting state to ready")
        unstashAll()
        context.become(ready(services))
      }

    // save these messages for later when in the ready state
    case s: SubmitWithRunId => stash()
    case s: CommandStatus => stash()
    case c: ConfigGet => stash()
    case c: ConfigControlMessage => stash()

    case Terminated(actorRef) =>

    case ConfigPut(config) => internalConfig(config)

    case x => log.error(s"Unexpected message from $sender (while waiting for services): $x")
  }

  // Messages received in the ready state.
  def ready(services: List[LocationServiceInfo]): Receive = {
    case s: SubmitWithRunId => submit(s, services)

    case status: CommandStatus =>
      if (status.done) {
        forwardToSubmitWorker(status.runId, status)
        workers -= status.runId
      }

    case ConfigGet(config) => query(config, services)
    case ConfigPut(config) => internalConfig(config)

    case c: ConfigControlMessage => forwardToSubmitWorker(c.runId, c)

    // If a target actor died, go back and wait for it (and any others that are needed) to restart
    case Terminated(actorRef) =>
      log.info(s"Received terminated message for $actorRef: Switch to waitingForServices state")
      LocationService.requestServices(context.system, self, services.map(_.serviceId))
      context.become(waitingForServices)

    case x => log.error(s"Unexpected message from $sender: $x")
  }

  // Forwards the given message to the submit worker actor
  private def forwardToSubmitWorker(runId: RunId, msg: AnyRef) : Unit = {
    workers.get(runId).fold(log.warning(s"Received message $msg for unknown or already completed runId: $runId")) {
      _ forward msg
    }
  }

  /**
   * Called when a config is submitted.
   * Send each actor that registered for a config path that part of the config, if found,
   * and save a list so we can check if all are done later when the status messages are received.
   */
  private def submit(submit: SubmitWithRunId, targetActors: List[LocationServiceInfo]): Unit = {
    // First get a list of the config parts we need to send and the target actors that should get them
    val submitInfoList = targetActors.map(getSubmitInfo(submit, _)).flatten.toList

    if (submitInfoList.length == 0) {
      log.error(s"No subscribers for submit: ${submit.config}")
      commandStatusActor ! CommandStatusActor.StatusUpdate(CommandStatus.Error(submit.runId, "No subscribers"), submit.submitter)
    } else {
      // Create a dedicated submit worker actor to handle this command
      val worker = context.actorOf(SubmitWorkerActor.props(commandStatusActor, submit.runId, submit.submitter, submitInfoList))
      workers += (submit.runId -> worker)
      submitInfoList.foreach(s => workers += (s.submit.runId -> worker))
      commandStatusActor ! CommandStatusActor.StatusUpdate(CommandStatus.Busy(submit.runId), submit.submitter)
    }
  }

  // Returns given config if pathOpt is None, otherwise the config at the given path in the given config.
  private def getConfig(config: Configuration, pathOpt: Option[String]): Configuration = {
    if (pathOpt.isEmpty) config else config.getConfig(pathOpt.get)
  }

  // Returns Some(SubmitInfo) if there is a matching path in the config to be submitted, otherwise None.
  private def getSubmitInfo(submit: SubmitWithRunId, targetActor: LocationServiceInfo): Option[SubmitInfo] = {
    val pathOpt = targetActor.configPathOpt
    if (targetActor.actorRefOpt.isDefined && (pathOpt.isEmpty || submit.config.hasPath(pathOpt.get))) {
      // Give each config part a unique runid, so we can identify it later when the status is received
      val runIdPart = RunId()
      val config = getConfig(submit.config, pathOpt)
      val submitPart = SubmitWithRunId(config, self, runIdPart)
      Some(SubmitInfo(pathOpt, submitPart, targetActor.actorRefOpt.get))
    } else None
  }

  /**
   * Query the current state of a device and reply to the given actor with a ConfigResponse object.
   * A config is passed in (the values are ignored) and the reply will be sent containing the
   * same config with the current values filled out.
   *
   * @param config used to specify the keys for the values that should be returned
   * @param targetActors information about the target HCDs or assemblies
   */
  private def query(config: Configuration, targetActors: List[LocationServiceInfo]): Unit = {
    // Like submit, send parts of the query to the target actors and when all replies are in,
    // combine and return to sender.

    // First get a list of the config parts we need to send and the target actors that should get them.
    val list = targetActors.map {
      targetActor =>
        val pathOpt = targetActor.configPathOpt
        if (targetActor.actorRefOpt.isDefined && (pathOpt.isEmpty || config.hasPath(pathOpt.get))) {
          val msg = ConfigGet(getConfig(config, pathOpt))
          Some(QueryInfo(targetActor.actorRefOpt.get, msg, pathOpt))
        } else None
    }.flatten.toList

    if (list.length == 0) {
      log.error(s"No subscribers for config/get query: $config")
      sender ! ConfigResponse(Failure(new Error("No subscribers for config/get query")))
    } else {
      // Hand this off to a new query worker actor to gather the results from the target actors
      context.actorOf(QueryWorkerActor.props(list, sender))
    }
  }

  /**
   * Used to configure the system (for internal use)
   * @param config contains internal configuration values (to be defined)
   */
  private def internalConfig(config: Configuration): Unit = {
    // XXX TODO to be defined...
  }

}


// ---- SubmitWorkerActor ----


/**
 * Defines props to create the submit worker actor
 */
private object SubmitWorkerActor {
  def props(commandStatusActor: ActorRef, runId: RunId, submitter: ActorRef, submitInfoList: List[SubmitInfo]): Props =
    Props(classOf[SubmitWorkerActor], commandStatusActor, runId, submitter, submitInfoList)
}

/**
 * One of these actors is created for each submitted command to wait for the different parts to complete and
 * then send the status to the command status actor
 *
 * @param commandStatusActor reference to the command status actor, which receives the final status of command
 *                           as well as partial status updates.
 * @param runId the runId of the original command
 * @param submitter: the original submitter
 * @param submitInfoList list of subtasks (submit messages and the actors that should receive them)
 */
private class SubmitWorkerActor(commandStatusActor: ActorRef,
                                runId: RunId, submitter: ActorRef,
                                submitInfoList: List[SubmitInfo]) extends Actor with ActorLogging {

  // Use waiting state to keep track of remaining parts and the final return status
  context.become(waiting(submitInfoList, CommandStatus.Submitted(runId)))

  // Send the submit messages to the target actors
  submitInfoList.foreach {
    submitInfo =>
      log.info(s"Sending config part to ${submitInfo.target}: ${submitInfo.submit.config}")
      submitInfo.target ! submitInfo.submit
  }

  /**
   * State where we are waiting for the different parts of the config to complete.
   * @param parts list of config message parts that were sent to different actors
   * @param returnStatus the current return status (calculated from the status values received so far)
   */
  def waiting(parts: List[SubmitInfo], returnStatus: CommandStatus): Receive = {
    // Status Results for a config part from a ConfigActor: check if all parts have completed
    case status: CommandStatus if status.done =>
      val newStatus = getCommandStatus(status, returnStatus)
      val (completedParts, remainingParts) = parts.partition(_.submit.runId == status.runId)
      checkIfDone(completedParts, remainingParts, newStatus)

    // Forward any cancel, abort, pause, resume messages to the target actors
    case c: ConfigControlMessage =>
      parts.foreach {
        part => part.target ! c.withRunId(part.submit.runId)
      }

    case x => log.error(s"Unexpected message from $sender: $x")
  }

  // This state is not used here
  override def receive: Receive = {
    case x => log.error(s"Unexpected message received from $sender: $x")
  }

  /**
   * If all of the config parts are done, send the final status to the original sender.
   * @param completedParts list of the parts of the config that have just completed (normally just contains one item)
   * @param remainingParts list of the parts of the config that have not yet completed
   * @param commandStatus the status of the config part from the worker actor
   */
  private def checkIfDone(completedParts: List[SubmitInfo], remainingParts: List[SubmitInfo], commandStatus: CommandStatus): Unit = {
    if (remainingParts.isEmpty) {
      // done, return status to sender
      log.debug(s"All config parts done: Returning $commandStatus for submitter $submitter")
      commandStatusActor ! CommandStatusActor.StatusUpdate(commandStatus, submitter)
      context.stop(self)
    } else {
      // There are still some parts remaining
      log.debug(s"${remainingParts.length} parts left for runId $runId")

      // send partially complete status (may be displayed by UI)
      completedParts.foreach { part =>
        val partialStatus = CommandStatus.PartiallyCompleted(runId, part.path, commandStatus.name)
        commandStatusActor ! CommandStatusActor.StatusUpdate(partialStatus, submitter)
        log.info(s"Status: PartiallyCompleted: path = ${part.path}")
      }

      context.become(waiting(remainingParts, commandStatus))
    }
  }

  /**
   * Returns a CommandStatus with the runId for the original submit.
   * If a part of the config was canceled or aborted, or if there was an error, then that status is returned
   * with the given runId.
   */
  private def getCommandStatus(newStatus: CommandStatus, oldStatus: CommandStatus): CommandStatus = {
    val s = oldStatus match {
      case CommandStatus.Canceled(_) => oldStatus.withRunId(runId)
      case CommandStatus.Aborted(_) => oldStatus.withRunId(runId)
      case CommandStatus.Error(_, msg) => oldStatus.withRunId(runId)
      case _ => newStatus.withRunId(runId)
    }
    log.debug(s"Old status: $oldStatus, new status: $newStatus ==> $s")
    s
  }
}




// ---- QueryWorkerActor ----




/**
 * Defines props to create the query worker actor
 */
private object QueryWorkerActor {
  // Query info broken down by target actor and optional path
  case class QueryInfo(targetActor: ActorRef, msg: ConfigActor.ConfigGet, queryPath: Option[String])

  def props(list: List[QueryInfo], replyTo: ActorRef): Props = Props(classOf[QueryWorkerActor], list, replyTo)
}

/**
 * One of these actors is created for each query command to wait for the different parts to reply and
 * then send the result to the replyTo actor.
 */
private class QueryWorkerActor(list: List[QueryInfo], replyTo: ActorRef)
  extends Actor with ActorLogging {

  query(list, replyTo)

  // This state is not used here (yet)
  override def receive: Receive = {
    case x => log.error(s"Unexpected message received from $sender: $x")
  }

  /**
   * Query the current state of a device and reply to the given actor with a ConfigResponse object.
   * A config is passed in (the values are ignored) and the reply will be sent containing the
   * same config with the current values filled out.
   *
   * @param list list of query info for each target actor and path
   * @param replyTo send the answer this actor
   */
  private def query(list: List[QueryInfo], replyTo: ActorRef): Unit = {
    // Like submit, send parts of the query to the registered config actors and when all replies are in,
    // combine and return to sender.

    implicit val execContext = context.dispatcher
    implicit val askTimeout = Timeout(3 seconds) // TODO: Implement this with tell or configure timeout?

    val listOfFutureResponses =
      for (queryInfo <- list) yield
        (queryInfo.targetActor ? queryInfo.msg).mapTo[ConfigResponse].map(insertPath(_, queryInfo.queryPath))
    Future.sequence(listOfFutureResponses).onComplete {
      case Success(responseList) =>
        replyTo ! mergeConfigResponses(responseList.toList)
        context.stop(self)
      case Failure(ex) =>
        replyTo ! ConfigResponse(Failure(ex))
        context.stop(self)
    }
  }

  /**
   * Returns the given response with the config modified by inserting it at the given path.
   * This is to make up for the fact that the config actor only received and filled out
   * a part of the config. This method puts that part back in its place in the config tree.
   * @param response the response to a "get" query from the config actor
   * @param path an optional path in the config that the actor registered for
   * @return the response with the config inserted at the given path
   */
  private def insertPath(response: ConfigResponse, path: Option[String]): ConfigResponse = {
    response.tryConfig match {
      case Success(config) =>
        if (path.isEmpty) {
          response
        } else {
          val map = Map(path.get -> config.asMap(""))
          ConfigResponse(Success(Configuration(map)))
        }
      case Failure(ex) =>
        response
    }
  }

  /**
   * Merges the list of responses to a single response.
   * @param responses the responses from different config actors
   * @return the merged response
   */
  private def mergeConfigResponses(responses: List[ConfigResponse]): ConfigResponse = {
    // return first failure if found, otherwise the merged response
    val configs = for(resp <- responses) yield
      resp.tryConfig match {
        case Success(config) =>
          config
        case Failure(ex) =>
          return resp
      }

    ConfigResponse(Success(Configuration.merge(configs.toList)))
  }
}
