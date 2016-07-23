package csw.util.akka

import akka.actor.{Actor, ActorLogging}
import akka.event.Logging.LogLevel
import akka.event.{BusLogging, LoggingAdapter}

// Wrapper for Akka's BusLogging that inserts a prefix in the message, if it is not empty
private case class PrefixedLogging(log: BusLogging, prefix: String) extends BusLogging(log.bus, log.logSource, log.logClass) {
  private def fmt(message: String): String = {
    if (prefix.nonEmpty) s"prefix=$prefix: $message" else message
  }

  override def isErrorEnabled = log.isErrorEnabled
  override def isWarningEnabled = log.isWarningEnabled
  override def isInfoEnabled = log.isInfoEnabled
  override def isDebugEnabled = log.isDebugEnabled

  override def error(cause: Throwable, message: String): Unit = log.error(cause, fmt(message))

  override def error(cause: Throwable, template: String, arg1: Any): Unit = log.error(cause, fmt(template), arg1)

  override def error(cause: Throwable, template: String, arg1: Any, arg2: Any): Unit = log.error(cause, fmt(template), arg1, arg2)

  override def error(cause: Throwable, template: String, arg1: Any, arg2: Any, arg3: Any): Unit = log.error(cause, fmt(template), arg1, arg2, arg3)

  override def error(cause: Throwable, template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = log.error(cause, fmt(template), arg1, arg2, arg3, arg4)

  override def error(message: String): Unit = log.error(fmt(message))

  override def error(template: String, arg1: Any): Unit = log.error(fmt(template), arg1)

  override def error(template: String, arg1: Any, arg2: Any): Unit = log.error(fmt(template), arg1, arg2)

  override def error(template: String, arg1: Any, arg2: Any, arg3: Any): Unit = log.error(fmt(template), arg1, arg2, arg3)

  override def error(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = log.error(fmt(template), arg1, arg2, arg3, arg4)

  override def warning(message: String): Unit = log.warning(fmt(message))

  override def warning(template: String, arg1: Any): Unit = log.warning(fmt(template), arg1)

  override def warning(template: String, arg1: Any, arg2: Any): Unit = log.warning(fmt(template), arg1, arg2)

  override def warning(template: String, arg1: Any, arg2: Any, arg3: Any): Unit = log.warning(fmt(template), arg1, arg2, arg3)

  override def warning(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = log.warning(fmt(template), arg1, arg2, arg3, arg4)

  override def info(message: String): Unit = log.info(fmt(message))

  override def info(template: String, arg1: Any): Unit = log.info(fmt(template), arg1)

  override def info(template: String, arg1: Any, arg2: Any): Unit = log.info(fmt(template), arg1, arg2)

  override def info(template: String, arg1: Any, arg2: Any, arg3: Any): Unit = log.info(fmt(template), arg1, arg2, arg3)

  override def info(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = log.info(fmt(template), arg1, arg2, arg3, arg4)

  override def debug(message: String): Unit = log.debug(fmt(message))

  override def debug(template: String, arg1: Any): Unit = log.debug(fmt(template), arg1)

  override def debug(template: String, arg1: Any, arg2: Any): Unit = log.debug(fmt(template), arg1, arg2)

  override def debug(template: String, arg1: Any, arg2: Any, arg3: Any): Unit = log.debug(fmt(template), arg1, arg2, arg3)

  override def debug(template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = log.debug(fmt(template), arg1, arg2, arg3, arg4)

  override def log(level: LogLevel, message: String): Unit = log.log(level, fmt(message))

  override def log(level: LogLevel, template: String, arg1: Any): Unit = log.log(level, fmt(template), arg1)

  override def log(level: LogLevel, template: String, arg1: Any, arg2: Any): Unit = log.log(level, fmt(template), arg1, arg2)

  override def log(level: LogLevel, template: String, arg1: Any, arg2: Any, arg3: Any): Unit = log.log(level, fmt(template), arg1, arg2, arg3)

  override def log(level: LogLevel, template: String, arg1: Any, arg2: Any, arg3: Any, arg4: Any): Unit = log.log(level, fmt(template), arg1, arg2, arg3, arg4)

  override def format(t: String, arg: Any*): String = log.format(t, arg)
}

/**
 * Like Akka's ActorLogging, but inserts the given prefix in the log messages, if it is not empty
 */
trait PrefixedActorLogging extends ActorLogging { this: Actor ⇒
  private var _log: LoggingAdapter = _

  def prefix: String

  override def log: LoggingAdapter = {
    // only used in Actor, i.e. thread safe
    if (_log eq null)
      _log = akka.event.Logging(context.system, this)
    PrefixedLogging(_log.asInstanceOf[BusLogging], prefix)
  }
}

