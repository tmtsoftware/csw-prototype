package csw.services.alarms

import akka.util.ByteString
import com.typesafe.config.Config
import redis.{ByteStringDeserializer, ByteStringDeserializerDefault}

/**
 * Basic model for an Alarm.
 * This information is read from the Alarm Service Config File and stored in Redis
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
   * @return the unique key to use to store the static data for this alarm in the database
   */
  def key(): String = AlarmModel.makeKey(subsystem, component, name)

  /**
   * @return the unique key to use to store the severity for this alarm in the database
   */
  def severityKey(): String = s"${key()}.severity"

  /**
   * @return The contents of this object as a map
   */
  def map(): Map[String, String] = {
    Map(
      "subsystem" → subsystem,
      "component" → component,
      "name" → name,
      "description" → description,
      "location" → location,
      "alarmType" → alarmType.toString,
      "severityLevels" → severityLevels.mkString(":"),
      "probableCause" → probableCause,
      "operatorResponse" → operatorResponse,
      "acknowledge" → acknowledge.toString,
      "latched" → latched.toString
    )
  }
}

object AlarmModel extends ByteStringDeserializerDefault {

  import net.ceedubs.ficus.Ficus._

  /**
   * Base trait for severity levels
   */
  sealed trait SeverityLevel

  object SeverityLevel {

    case object Indeterminate extends SeverityLevel

    case object Okay extends SeverityLevel

    case object Warning extends SeverityLevel

    case object Major extends SeverityLevel

    case object Critical extends SeverityLevel

    def apply(name: String): SeverityLevel = name match {
      case "Indeterminate" ⇒ Indeterminate
      case "Okay"          ⇒ Okay
      case "Warning"       ⇒ Warning
      case "Major"         ⇒ Major
      case "Critical"      ⇒ Critical
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
      case "Absolute"     ⇒ Absolute
      case "BitPattern"   ⇒ BitPattern
      case "Calculated"   ⇒ Calculated
      case "Deviation"    ⇒ Deviation
      case "Discrepancy"  ⇒ Discrepancy
      case "Instrument"   ⇒ Instrument
      case "RateChange"   ⇒ RateChange
      case "RecipeDriven" ⇒ RecipeDriven
      case "Safety"       ⇒ Safety
      case "Statistical"  ⇒ Statistical
      case "System"       ⇒ System
    }
  }

  /**
   * Builds a Redis key pattern to use to lookup alarm data
   *
   * @param subsystemOpt optional subsystem (default: any)
   * @param componentOpt optional component (default: any)
   * @param nameOpt      optional alarm name (default: any)
   * @return the key
   */
  def makeKeyPattern(subsystemOpt: Option[String], componentOpt: Option[String], nameOpt: Option[String]): String = {
    val subsystem = subsystemOpt.getOrElse("*")
    val component = componentOpt.getOrElse("*")
    val name = nameOpt.getOrElse("*")
    makeKey(subsystem, component, name)
  }

  /**
   * Builds the key used to store and lookup alarm data
   *
   * @param subsystem alarm's subsystem
   * @param component alarm's component
   * @param name      alarm's name
   * @return the key
   */
  def makeKey(subsystem: String, component: String, name: String): String = {
    s"alarm:$subsystem.$component.$name"
  }

  /**
   * Given a key, return the subsystem, component and name
   *
   * @param key the key used to store the alarm data in Redis
   * @return (subsystem, component, name)
   */
  def parseKey(key: String): (String, String, String) = {
    val t = key.split(':')(1).split('.')
    (t(0), t(1), t(2))
  }

  /**
   * Initializes an AlarmModel from the given Config
   *
   * @param config a config created from a file in the format described by the alarm-schema
   * @return the alarm model
   */
  def apply(config: Config): AlarmModel = {
    AlarmModel(
      subsystem = config.as[String]("subsystem"),
      component = config.as[String]("component"),
      name = config.as[String]("name"),
      description = config.as[String]("description"),
      location = config.as[String]("location"),
      alarmType = AlarmType(config.as[String]("alarmType")),
      severityLevels = config.as[List[String]]("severityLevels").map(SeverityLevel(_)),
      probableCause = config.as[String]("probableCause"),
      operatorResponse = config.as[String]("operatorResponse"),
      acknowledge = config.as[Boolean]("acknowledge"),
      latched = config.as[Boolean]("latched")
    )
  }

  /**
   * Initializes an AlarmModel from the given map
   *
   * @param map a map, as returned from Redis hgetall
   * @return the alarm model
   */
  def apply(map: Map[String, ByteString]): AlarmModel = {
    val formatter = implicitly[ByteStringDeserializer[String]]

    val subsystem = formatter.deserialize(map("subsystem"))
    val component = formatter.deserialize(map("component"))
    val name = formatter.deserialize(map("name"))
    val description = formatter.deserialize(map("description"))
    val location = formatter.deserialize(map("location"))
    val alarmType = AlarmType(formatter.deserialize(map("alarmType")))
    val severityLevels = formatter.deserialize(map("severityLevels")).split(":").toList.map(SeverityLevel(_))
    val probableCause = formatter.deserialize(map("probableCause"))
    val operatorResponse = formatter.deserialize(map("operatorResponse"))
    val acknowledge = formatter.deserialize(map("acknowledge")).toBoolean
    val latched = formatter.deserialize(map("latched")).toBoolean

    AlarmModel(subsystem, component, name, description, location, alarmType, severityLevels, probableCause,
      operatorResponse, acknowledge, latched)
  }
}

