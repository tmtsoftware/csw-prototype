package org.tmt.csw.test.container1

import akka.actor.Props
import org.tmt.csw.pkg.Assembly

object Assembly1 {
  def props(name: String): Props = Props(classOf[Assembly1], name)
}

// A test assembly
case class Assembly1(name: String) extends Assembly {

  def receive: Receive = receiveAssemblyMessages

  def initialize(): Unit = {log.info("Assembly1 initialize")}

  def startup(): Unit = {log.info("Assembly1 startup")}

  def run(): Unit = {log.info("Assembly1 run")}

  def shutdown(): Unit = {log.info("Assembly1 shutdown")}

  def uninit(): Unit = {log.info("Assembly1 uninit")}

  def remove(): Unit = {log.info("Assembly1 remove")}
}
