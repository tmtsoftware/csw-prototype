package csw.services.alarms

/**
 * Methods to create keys used to store alarm data and publish the severity.
 */
object AlarmKey {
  // Prefix for the static alarm data (See AlarmModel)
  private val alarmKeyPrefix = "alarm:"

  // Prefix for storing the alarm's severity level
  private val severityKeyPrefix = "severity:"

  // Prefix for storing the alarm's state (latched, acknowledged, etc.)
  private val alarmStateKeyPrefix = "astate:"

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
   * Creates an AlarmKey fom the string representation of either key or severityKey.
   * @param key the value returned for key or severityKey
   * @return the AlarmKey instance
   */
  def apply(key: String): AlarmKey = {
    val k = if (key.startsWith(severityKeyPrefix))
      key.substring(severityKeyPrefix.length)
    else key
    val subsystem :: component :: name :: _ = k.substring(alarmKeyPrefix.length).split(':').toList
    AlarmKey(subsystem, component, name)
  }
}

/**
 * Represents a key used to store alarm data and publish the severity.
 */
case class AlarmKey(subsystem: String, component: String, name: String) {

  import AlarmKey._

  val key = s"$alarmKeyPrefix$subsystem:$component:$name"
  val severityKey = severityKeyPrefix + key
  val stateKey = alarmStateKeyPrefix + key
}
