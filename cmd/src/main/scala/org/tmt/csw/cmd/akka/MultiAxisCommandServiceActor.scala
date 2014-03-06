package org.tmt.csw.cmd.akka

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import org.tmt.csw.cmd.core.Configuration

/**
 * This is like [[org.tmt.csw.cmd.akka.CommandServiceActor]], but provides multiple separate queues
 * for use with multi-axis devices where each axis can do one thing at a time.
 */
trait MultiAxisCommandServiceActor extends Actor with ActorLogging {
  import CommandServiceActor._
  import CommandQueueActor._
  import ConfigActor._

  /**
   * The number of axes
   */
  def axisCount: Int

  // actors receiving config and command status messages and passing them to subscribers
  val commandStatusActors = new Array[ActorRef](axisCount)
  for (i <- 0 to axisCount) {
    commandStatusActors(i) = context.actorOf(Props[CommandStatusActor], name = commandStatusActorName+i)
  }

  // Create the queue actors
  val commandQueueActors = new Array[ActorRef](axisCount)
  for (i <- 0 to axisCount) {
    commandQueueActors(i) = context.actorOf(CommandQueueActor.props(commandStatusActors(i)), name = commandQueueActorName+i)
  }

  // The actors that will process the configs
  val configActors = new Array[ActorRef](axisCount)
  def initConfigActors(): Unit

  // Connect the config actors, which are defined later in a derived class, to the queues on start
  override def preStart(): Unit = {
    for (i <- 0 to axisCount) {
      commandQueueActors(i) ! CommandQueueActor.QueueClient(configActors(i))
    }
  }

  // The queue controller actor
  val commandQueueControllerActors = new Array[ActorRef](axisCount)
  def initCommandQueueControllerActors(): Unit

  /**
   * Returns the axis index for the given configuration
   */
  def axisIndexForConfig(config: Configuration): Int


  // Needed for "ask"
  private implicit val execContext = context.dispatcher

  // Receive only the command server commands
  // XXX TODO: intercept command status so we can return a single status even if config references multiple axes!
  def receiveCommands: Receive = {
    // Queue related commands
    case Submit(config, submitter) =>
      val i = axisIndexForConfig(config)
      commandQueueActors(i) forward SubmitWithRunId(config, submitter)

    case s@SubmitWithRunId(config, submitter, runId) =>
      val i = axisIndexForConfig(config)
      commandQueueActors(i) forward s

    case QueueBypassRequest(config) =>
      val i = axisIndexForConfig(config)
      configActors(i) forward SubmitWithRunId(config, sender)

    case QueueBypassRequestWithRunId(config, submitter, runId) =>
      val i = axisIndexForConfig(config)
      configActors(i) forward SubmitWithRunId(config, submitter, runId)

    case s@QueueStop => for (a <- commandQueueActors) a forward s
    case s@QueuePause => for (a <- commandQueueActors) a forward s
    case s@QueueStart => for (a <- commandQueueActors) a forward s
    case s@QueueDelete(runId) => for (a <- commandQueueActors) a forward s
    case configMessage: ConfigMessage => for (a <- configActors) a forward configMessage
  }
}

