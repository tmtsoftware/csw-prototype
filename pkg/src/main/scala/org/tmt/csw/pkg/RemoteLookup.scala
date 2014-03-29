package org.tmt.csw.pkg

import scala.concurrent.duration._
import akka.actor._
import akka.actor.ActorIdentity
import scala.Some
import akka.actor.Identify

object RemoteLookup {
  def props(path: String): Props = Props(classOf[RemoteLookup], path)
}

/**
 * Modified example from Akka in Action book, might be useful here to deal with remote actors
 * @param path Akka URI of a remote actor
 */
case class RemoteLookup(path: String) extends Actor with ActorLogging {

  private var backlog = List.empty[(Any, ActorRef)]

  context.setReceiveTimeout(3 seconds)
  sendIdentifyRequest()

  def sendIdentifyRequest(): Unit = {
    log.info(s"sendIdentifyRequest $path")
    val selection = context.actorSelection(path)
    selection ! Identify(path)
  }

  def receive: Receive = identify

  def identify: Receive = {
    case ActorIdentity(`path`, Some(actor)) =>
      context.setReceiveTimeout(Duration.Undefined)
      log.info("switching to active state")
      context.become(active(actor))
      context.watch(actor)
      // Forward any saved messages
      backlog.reverse.foreach {
        case (msg, actorRef) =>
          actor.tell(msg, actorRef)
      }
      backlog = List.empty[(Any, ActorRef)]

    case ActorIdentity(`path`, None) =>
      log.warning(s"Remote actor with path $path is not currently available (will keep trying).")
      Thread.sleep(1000) // XXX?
      sendIdentifyRequest()

    case ReceiveTimeout =>
      sendIdentifyRequest()

    case msg: Any =>
      backlog = (msg, sender()) :: backlog
      log.warning(s"Saving message for later: $msg, remote actor is not ready yet.")
  }

  def active(actor: ActorRef): Receive = {
    case Terminated(actorRef) =>
      log.info(s"Actor $actorRef terminated: switching to identify state.")
      context.become(identify)
      context.setReceiveTimeout(3 seconds)
      sendIdentifyRequest()

    case msg: Any => actor forward msg
  }
}
