package csw.services.alarms

/**
 * Methods to create keys used to store alarm data and publish the severity.
 */
object AlarmKey {
  // Prefix for the static alarm data (See AlarmModel)
  private[alarms] val alarmKeyPrefix = "alarm::"

  // Prefix for storing the alarm's severity level
  private[alarms] val severityKeyPrefix = "severity::"

  // Prefix for storing the alarm's state (latched, acknowledged, etc.)
  private[alarms] val alarmStateKeyPrefix = "astate::"

  // Separator for subsystem, component, name in alarm key
  private val sep = ':'

  /**
   * Creates an alarm key from the information in the model
   *
   * @param a the alarm model
   * @return the alarm key
   */
  def apply(a: AlarmModel): AlarmKey = AlarmKey(a.subsystem, a.component, a.name)

  /**
   * Creates an alarm key from the given optional subsystem, component and name, using wildcards in place of None.
   * The key may match multiple alarms if any of the arguments are empty or contain Redis wildcards.
   *
   * @param subsystemOpt optional subsystem (default: any)
   * @param componentOpt optional component (default: any)
   * @param nameOpt      optional alarm name (default: any)
   * @return the alarm key
   */
  def apply(subsystemOpt: Option[String] = None, componentOpt: Option[String] = None, nameOpt: Option[String] = None): AlarmKey = {
    val subsystem = subsystemOpt.getOrElse("*")
    val component = componentOpt.getOrElse("*")
    val name = nameOpt.getOrElse("*")
    AlarmKey(subsystem, component, name)
  }

  /**
   * Creates an AlarmKey from the string representation.
   *
   * @param key one of the string values for key (key,severityKey, stateKey)
   * @return the AlarmKey instance
   */
  def apply(key: String): AlarmKey = {
    val k = if (key.contains("::")) key.substring(key.indexOf("::") + 2) else key
    val subsystem :: component :: name :: _ = k.split(sep).toList
    AlarmKey(subsystem, component, name)
  }
}

/**
 * Represents a key used to store alarm data and publish the severity.
 *
 * @param subsystem the subsystem for the component
 * @param component the component for the alarm
 * @param name the alarm name
 */
case class AlarmKey(subsystem: String, component: String, name: String) {
  import AlarmKey._
  private val k = s"$subsystem$sep$component$sep$name"

  val key = alarmKeyPrefix + k
  val severityKey = severityKeyPrefix + k
  val stateKey = alarmStateKeyPrefix + k
}
