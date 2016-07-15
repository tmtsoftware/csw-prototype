package csw.services.alarms

import akka.util.ByteString
import com.typesafe.config.Config
import csw.services.alarms.AlarmModel.SeverityLevel
import redis.{ByteStringDeserializer, ByteStringDeserializerDefault}

/**
 * Basic model for an Alarm.
 * This information is read from the Alarm Service Config File and stored in Redis
 *
 * @param subsystem The alarm belongs to this subsystem
 * @param component The alarm belongs to this component
 * @param name the name of the alarm
 * @param description a description of the alarm
 * @param location A text description of where the alarming condition is located
 * @param alarmType The general category for the alarm (e.g., limit alarm)
 * @param severityLevels Severity levels implemented by the component alarm
 * @param probableCause The probable cause for each level or for all levels
 * @param operatorResponse Instructions or information to help the operator respond to the alarm.
 * @param acknowledge Does this alarm require an acknowledge by the operator?
 * @param latched Should this alarm be latched?
 */
case class AlarmModel(
    subsystem:        String,
    component:        String,
    name:             String,
    description:      String,
    location:         String,
    alarmType:        AlarmModel.AlarmType,
    severityLevels:   List[AlarmModel.SeverityLevel],
    probableCause:    String,
    operatorResponse: String,
    acknowledge:      Boolean,
    latched:          Boolean
) {

  /**
   * @return The contents of this object as a map
   */
  def asMap(): Map[String, String] = {
    import AlarmModel.F
    Map(
      F.subsystem -> subsystem,
      F.component -> component,
      F.name -> name,
      F.description -> description,
      F.location -> location,
      F.alarmType -> alarmType.toString,
      F.severityLevels -> severityLevels.mkString(":"),
      F.probableCause -> probableCause,
      F.operatorResponse -> operatorResponse,
      F.acknowledge -> acknowledge.toString,
      F.latched -> latched.toString
    )
  }
}

object AlarmModel extends ByteStringDeserializerDefault {

  // Field name constants
  private[alarms] object F {
    val subsystem = "subsystem"
    val component = "component"
    val name = "name"
    val description = "description"
    val location = "location"
    val alarmType = "alarmType"
    val severityLevels = "severityLevels"
    val probableCause = "probableCause"
    val operatorResponse = "operatorResponse"
    val acknowledge = "acknowledge"
    val latched = "latched"
  }

  import net.ceedubs.ficus.Ficus._

  /**
   * Base trait for severity levels
   */
  sealed trait SeverityLevel {
    /**
     * A numeric level for the severity.
     * Higher values are more critical.
     * Anything greater than 0 is not OK.
     */
    val level: Int

    /**
     * Returns true if the value represents an alarm condition (i.e.: its not Okay)
     */
    def isAlarm: Boolean = level > 0 // XXX TODO: How are Disconnected and Indeterminate handled for latching?

    /**
     * String representation
     */
    def name: String

    override def toString = name
  }

  object SeverityLevel {

    abstract class SeverityLevelBase(override val level: Int, override val name: String) extends SeverityLevel

    case object Disconnected extends SeverityLevelBase(-2, "Disconnected")

    case object Indeterminate extends SeverityLevelBase(-1, "Indeterminate")

    case object Okay extends SeverityLevelBase(0, "Okay")

    case object Warning extends SeverityLevelBase(1, "Warning")

    case object Major extends SeverityLevelBase(2, "Major")

    case object Critical extends SeverityLevelBase(3, "Critical")

    def apply(name: String): Option[SeverityLevel] = name match {
      case Disconnected.name  => Some(Disconnected)
      case Indeterminate.name => Some(Indeterminate)
      case Okay.name          => Some(Okay)
      case Warning.name       => Some(Warning)
      case Major.name         => Some(Major)
      case Critical.name      => Some(Critical)
      case _                  => None
    }
  }

  /**
   * Base trait for alarm types
   */
  sealed trait AlarmType

  case object AlarmType {

    case object Absolute extends AlarmType

    case object BitPattern extends AlarmType

    case object Calculated extends AlarmType

    case object Deviation extends AlarmType

    case object Discrepancy extends AlarmType

    case object Instrument extends AlarmType

    case object RateChange extends AlarmType

    case object RecipeDriven extends AlarmType

    case object Safety extends AlarmType

    case object Statistical extends AlarmType

    case object System extends AlarmType

    def apply(name: String): AlarmType = name match {
      case "Absolute"     => Absolute
      case "BitPattern"   => BitPattern
      case "Calculated"   => Calculated
      case "Deviation"    => Deviation
      case "Discrepancy"  => Discrepancy
      case "Instrument"   => Instrument
      case "RateChange"   => RateChange
      case "RecipeDriven" => RecipeDriven
      case "Safety"       => Safety
      case "Statistical"  => Statistical
      case "System"       => System
    }
  }

  /**
   * Base trait for health types
   */
  sealed trait Health

  case object Health {

    case object Good extends Health

    case object Bad extends Health

    case object Ill extends Health

  }

  /**
   * Combines an alarm key with the current severity level and state for the alarm
   *
   * @param alarmKey  the unique key for the alarm
   * @param severity the current alarm severity level
   * @param state    the current alarm state (indicates if the alarm needs acknowledgement, etc.)
   */
  case class AlarmStatus(alarmKey: AlarmKey, severity: SeverityLevel, state: AlarmState)

  /**
   * Combines an alarm key (which may use wildcards to match a system, subsystem or component)
   * with a health value, which is calculated from all the alarms matching the given alarm key.
   *
   * @param key    an alarm key matching all alarms for the system, a subsystem or component
   * @param health the total health, calculated from the severity values of all the alarms matching the key
   */
  case class HealthStatus(key: AlarmKey, health: Health)

  /**
   * Initializes an AlarmModel from the given Config
   *
   * @param config a config created from a file in the format described by the alarm-schema
   * @return the alarm model
   */
  def apply(config: Config): AlarmModel = {
    AlarmModel(
      subsystem = config.as[String](F.subsystem),
      component = config.as[String](F.component),
      name = config.as[String](F.name),
      description = config.as[String](F.description),
      location = config.as[String](F.location),
      alarmType = AlarmType(config.as[String](F.alarmType)),
      severityLevels = config.as[List[String]](F.severityLevels).map(SeverityLevel(_).getOrElse(SeverityLevel.Disconnected)),
      probableCause = config.as[String](F.probableCause),
      operatorResponse = config.as[String](F.operatorResponse),
      acknowledge = config.as[Boolean](F.acknowledge),
      latched = config.as[Boolean](F.latched)
    )
  }

  /**
   * Initializes an AlarmModel from the given map
   *
   * @param map a map, as returned from Redis hgetall
   * @return the alarm model, if found
   */
  def apply(map: Map[String, ByteString]): Option[AlarmModel] = {
    if (map.isEmpty) None else {
      val formatter = implicitly[ByteStringDeserializer[String]]

      val subsystem = formatter.deserialize(map(F.subsystem))
      val component = formatter.deserialize(map(F.component))
      val name = formatter.deserialize(map(F.name))
      val description = formatter.deserialize(map(F.description))
      val location = formatter.deserialize(map(F.location))
      val alarmType = AlarmType(formatter.deserialize(map(F.alarmType)))
      val severityLevels = formatter.deserialize(map(F.severityLevels)).split(":").toList.map(SeverityLevel(_).getOrElse(SeverityLevel.Disconnected))
      val probableCause = formatter.deserialize(map(F.probableCause))
      val operatorResponse = formatter.deserialize(map(F.operatorResponse))
      val acknowledge = formatter.deserialize(map(F.acknowledge)).toBoolean
      val latched = formatter.deserialize(map(F.latched)).toBoolean

      Some(AlarmModel(subsystem, component, name, description, location, alarmType, severityLevels, probableCause,
        operatorResponse, acknowledge, latched))
    }
  }
}

/**
 * An abbreviated alarm model that only contains the fields needed internally to calculate the severity, etc..
 *
 * @param severityLevels Severity levels implemented by the component alarm
 * @param acknowledge Does this alarm require an acknowledge by the operator?
 * @param latched Should this alarm be latched?
 */
private[alarms] case class AlarmModelSmall(severityLevels: List[AlarmModel.SeverityLevel], acknowledge: Boolean, latched: Boolean)

private[alarms] object AlarmModelSmall {
  def apply(seq: Seq[Option[ByteString]]): Option[AlarmModelSmall] = {
    val formatter = implicitly[ByteStringDeserializer[String]]
    for {
      severityLevels <- seq.head.map(formatter.deserialize(_).split(":").toList.map(SeverityLevel(_).getOrElse(SeverityLevel.Disconnected)))
      acknowledge <- seq(1).map(formatter.deserialize(_).toBoolean)
      latched <- seq(2).map(formatter.deserialize(_).toBoolean)
    } yield AlarmModelSmall(severityLevels, acknowledge, latched)
  }
}

