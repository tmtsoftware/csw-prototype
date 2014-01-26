package org.tmt.csw.test.container2

import akka.zeromq._
import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import akka.zeromq.Connect
import akka.zeromq.Listener
import akka.util.ByteString

object ZmqClient {
  def props(url: String): Props = Props(classOf[ZmqClient], url)

  // Type of a command sent to the ZMQ socket
  case class Command(m: ByteString)
}

/**
 * Akka ask (?) doesn't work when used on the akka-zeromq socket,
 * so this class can be put in between to make it work. See page 30 of:
 * http://www.slideshare.net/normation/nrm-scala-ioscalaetzeromqv10
 */
class ZmqClient(url: String) extends Actor with ActorLogging {

  val clientSocket = ZeroMQExtension(context.system).newSocket(
    SocketType.Req,
    Listener(self),
    Connect(url))

  override def receive: Receive = waitingForCommand

  def waitingForCommand: Receive = {
    case ZmqClient.Command(byteString) ⇒
      clientSocket ! ZMQMessage(byteString)
      context.become(waitingForReply(sender))
    case x ⇒ log.info(s"Unexpected Message from ZMQ: $x")
  }

  // Wait for the reply from the ZMQ socket and then forward it to the replyTo actor
  def waitingForReply(replyTo: ActorRef): Receive = {
    case m: ZMQMessage ⇒
      replyTo ! m
      context.become(waitingForCommand)
    case ZmqClient.Command(byteString) ⇒
      // Wait may have timed out, just continue with this new command
      clientSocket ! ZMQMessage(byteString)
      context.become(waitingForReply(sender))
    case x ⇒ log.info(s"Unexpected reply from ZMQ: $x")
  }
}
