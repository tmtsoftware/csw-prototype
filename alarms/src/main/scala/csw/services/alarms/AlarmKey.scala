package csw.services.alarms

/**
 * Methods to create keys used to store alarm data and publish the severity.
 */
object AlarmKey {
  private[alarms] val alarmKeyPrefix = "alarm:"
  private[alarms] val severityKeyPrefix = "severity:"

  /**
   * Creates an alarm key from the information in the model
   * @param a the alarm model
   * @return the alarm key
   */
  def apply(a: AlarmModel): AlarmKey = AlarmKey(a.subsystem, a.component, a.name)

  /**
   * Creates an alarm key from the given subsystem, component and name, using wildcards in place of None
   * @param subsystemOpt optional subsystem (default: any)
   * @param componentOpt optional component (default: any)
   * @param nameOpt      optional alarm name (default: any)
   * @return the alarm key
   */
  def apply(subsystemOpt: Option[String], componentOpt: Option[String], nameOpt: Option[String]): AlarmKey = {
    val subsystem = subsystemOpt.getOrElse("*")
    val component = componentOpt.getOrElse("*")
    val name = nameOpt.getOrElse("*")
    AlarmKey(subsystem, component, name)
  }

  /**
   * Builds the key used to store and lookup alarm data
   *
   * @param subsystem alarm's subsystem
   * @param component alarm's component
   * @param name      alarm's name
   * @return the key
   */
  private def makeKey(subsystem: String, component: String, name: String): String = {
    s"$alarmKeyPrefix$subsystem.$component.$name"
  }

  /**
   * Returns the key used to store and lookup alarm data.
   *
   * @param severityKey a key as returned from [[makeSeverityKey]]
   * @return the key
   */
  private[alarms] def makeKey(severityKey: String): String = {
    severityKey.substring(severityKeyPrefix.length)
  }

  /**
   * Builds the key used to store and lookup alarm data
   *
   * @param subsystem alarm's subsystem
   * @param component alarm's component
   * @param name      alarm's name
   * @return the key
   */
  private def makeSeverityKey(subsystem: String, component: String, name: String): String = {
    severityKeyPrefix + makeKey(subsystem, component, name)
  }
}

/**
 * Represents a key used to store alarm data and publish the severity.
 */
case class AlarmKey(subsystem: String, component: String, name: String) {
  import AlarmKey._

  val key = makeKey(subsystem, component, name)
  val severityKey = makeSeverityKey(subsystem, component, name)
}
