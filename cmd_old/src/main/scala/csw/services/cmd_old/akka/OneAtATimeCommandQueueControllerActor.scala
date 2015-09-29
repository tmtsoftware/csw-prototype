package csw.services.cmd_old.akka

import akka.actor.{ ActorRef, Props, ActorLogging, Actor }
import csw.shared.cmd.CommandStatus

object OneAtATimeCommandQueueControllerActor {
  /**
   * Used to create this actor.
   * @return the props to use to create the actor
   */
  def props(commandQueueActor: ActorRef, commandStatusActor: ActorRef): Props =
    Props(classOf[OneAtATimeCommandQueueControllerActor], commandQueueActor, commandStatusActor)
}

/**
 * Controls how configs are taken from the command queue.
 * This actor assumes the config actor can only handle one config at a time.
 */
class OneAtATimeCommandQueueControllerActor(commandQueueActor: ActorRef,
                                            commandStatusActor: ActorRef) extends Actor with ActorLogging {

  import CommandQueueActor._

  // Subscribe to receive status values from the status actor
  commandStatusActor ! CommandStatusActor.Subscribe(self)

  // Tell the queue actor this is the queue controller
  commandQueueActor ! CommandQueueActor.QueueController(self)

  override def receive: Receive = waitingForWork(1)

  // State where we are waiting for a work available message from the queue.
  // When we get the message, we request to dequeue the work and send it to the config actor.
  // Then we switch to waiting for the command status.
  // The count is used to keep track of work available message received while waiting for the command status
  def waitingForWork(count: Int): Receive = {
    case QueueWorkAvailable ⇒
      commandQueueActor ! Dequeue
      context become waitingForStatus(count)

    case commandStatus: CommandStatus ⇒ // Should not happen, unless actor was restarted
  }

  // State where we are waiting for the command status.
  // The count is used to keep track of work available message received while waiting for the command status
  def waitingForStatus(count: Int): Receive = {
    case QueueWorkAvailable ⇒
      context become waitingForStatus(count + 1)

    case commandStatus: CommandStatus ⇒
      if (commandStatus.done) {
        if (count <= 1) commandQueueActor ! Dequeue
        context become waitingForWork(count - 1)
      }
  }
}
