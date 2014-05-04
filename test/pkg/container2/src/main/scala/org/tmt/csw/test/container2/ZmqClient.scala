package org.tmt.csw.test.container2

import akka.actor.{Props, Actor, ActorLogging}
import akka.util.ByteString
import org.zeromq.ZMQ
import scala.concurrent.Future

object ZmqClient {
  def props(url: String): Props = Props(classOf[ZmqClient], url)

  // Type of a command sent to the ZMQ socket
  case class Command(m: ByteString)
}

/**
 * Using Jeromq, since akka-zeromq not available in Scala-2.11
 */
class ZmqClient(url: String) extends Actor with ActorLogging {
  import context.dispatcher

  val zmqContext = ZMQ.context(1)
  val socket = zmqContext.socket(ZMQ.REQ)
  socket.connect (url)

  override def receive: Receive = {
    case ZmqClient.Command(byteString) ⇒
      val replyTo = sender()
      Future {
        socket.send(byteString.toArray, 0)
        val reply = socket.recv(0)
        replyTo ! ByteString(reply)
      }

    case x ⇒ log.info(s"Unexpected Message from ZMQ: $x")
  }

//  def waitingForCommand: Receive = {
//    case ZmqClient.Command(byteString) ⇒
//      clientSocket ! ZMQMessage(byteString)
//      context.become(waitingForReply(sender))
//    case x ⇒ log.info(s"Unexpected Message from ZMQ: $x")
//  }
//
//  // Wait for the reply from the ZMQ socket and then forward it to the replyTo actor
//  def waitingForReply(replyTo: ActorRef): Receive = {
//    case m: ZMQMessage ⇒
//      replyTo ! m
//      context.become(waitingForCommand)
//    case ZmqClient.Command(byteString) ⇒
//      // Wait may have timed out, just continue with this new command
//      clientSocket ! ZMQMessage(byteString)
//      context.become(waitingForReply(sender))
//    case x ⇒ log.info(s"Unexpected reply from ZMQ: $x")
//  }
}
