package csw.util.akka

import akka.actor.Actor
import ch.qos.logback.classic._
import csw.services.log.PrefixedActorLogging
import org.slf4j.LoggerFactory

object SetLogLevelActor {

  /**
   * Message to set the log level for all packages under the given root package
   *
   * @param rootPackage the root java/scala package name for which the log level should be changed
   * @param level       one of OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL.
   */
  case class SetLogLevel(rootPackage: String, level: String)

}

/**
 * Adds the ability to receive a SetLogLevel(package, level) message in order
 * to set the log level for a package tree.
 */
trait SetLogLevelActor {
  this: Actor with PrefixedActorLogging =>

  import SetLogLevelActor._

  def logLevelReceive: Receive = {
    case SetLogLevel(pkg, level) =>
      val newLevel = Level.toLevel(level, null)
      if (newLevel != null) {
        log.debug(s"Setting log level for $self to $level")
        LoggerFactory.getLogger(pkg).asInstanceOf[Logger].setLevel(newLevel)
        log.debug(s"XXX Done setting log level for $self to $level")
      }
  }
}
