package org.tmt.csw.test.container2

import akka.actor.{ActorSystem, Props, Actor}
import akka.zeromq._
import akka.zeromq.Listener
import akka.util.ByteString

// Dummy class for testing
object HwClient {
  class Listener extends Actor {
    def receive: Receive = {
      case Connecting    ⇒ println("XXX ZMQ Connecting")

      case m: ZMQMessage ⇒
        println(s"XXX ZMQ Message: ${m.frame(0).utf8String}")
        sendMessage()

      case x  ⇒ println(s"XXX ZMQ Unknown Message: $x")
    }
  }

  var count = 0

  val system = ActorSystem("test")

  val listener = system.actorOf(Props(classOf[Listener]))

  val clientSocket = ZeroMQExtension(system).newSocket(
    SocketType.Req,
    Listener(listener),
    Connect("tcp://localhost:6565")
//    ,
//    Identity("1234-5678".getBytes)
  )


  def sendMessage(): Unit = {
    println("XXX sending dummy message")
    clientSocket ! ZMQMessage(ByteString(s"Dummy Message $count from Akka"))
  }

  def main(args: Array[String]): Unit = {
    sendMessage()
  }
}


