package csw.services.log

import akka.actor.Actor
import akka.event.DiagnosticLoggingAdapter

/**
 * An actor logger that inserts an MDC prefix field into the log, where prefix is the
 * component prefix, which is made up of the subsystem name, followed by a dot and the
 * rest of the component prefix.
 *
 * The prefix must be defined in a derived class. If it is the empty string, it is not included.
 */
trait PrefixedActorLogging { this: Actor ⇒
  private var _log: DiagnosticLoggingAdapter = _

  def prefix: String

  def log: DiagnosticLoggingAdapter = {
    import scala.collection.JavaConverters._
    // only used in Actor, i.e. thread safe
    if (_log eq null) {
      _log = akka.event.Logging(this)
      if (prefix.nonEmpty) {
        val map: Map[String, Any] = Map("prefix" -> prefix)
        _log.setMDC(map.asJava)
      }
    }
    _log
  }
}

