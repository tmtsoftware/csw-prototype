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
   * @return The contents of this object as a map
   */
  def asMap(): Map[String, String] = {
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
  sealed trait SeverityLevel {
    /**
     * A numeric level for the severity.
     * Higher values are more critical.
     * Anything greater than 0 is not OK.
     */
    val level: Int

    /**
     * Returns true if the value represents an alarm condition (i.e.: its not Okay or Indeterminate)
     */
    def isAlarm: Boolean = level > 0

    /**
     * String representation
     */
    def name: String

    override def toString = name
  }

  object SeverityLevel {

    abstract class SeverityLevelBase(override val level: Int, override val name: String) extends SeverityLevel

    case object Indeterminate extends SeverityLevelBase(-1, "Indeterminate")

    case object Okay extends SeverityLevelBase(0, "Okay")

    case object Warning extends SeverityLevelBase(1, "Warning")

    case object Major extends SeverityLevelBase(2, "Major")

    case object Critical extends SeverityLevelBase(3, "Critical")

    def apply(name: String): Option[SeverityLevel] = name match {
      case Indeterminate.name ⇒ Some(Indeterminate)
      case Okay.name          ⇒ Some(Okay)
      case Warning.name       ⇒ Some(Warning)
      case Major.name         ⇒ Some(Major)
      case Critical.name      ⇒ Some(Critical)
      case _                  ⇒ None
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
   * Combines the static alarm model with the current severity level for the alarm
   *
   * @param alarm the static alarm data
   * @param severity the current alarm severity level
   */
  case class AlarmStatus(alarm: AlarmModel, severity: SeverityLevel)

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
      severityLevels = config.as[List[String]]("severityLevels").map(SeverityLevel(_).getOrElse(SeverityLevel.Indeterminate)),
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
    val severityLevels = formatter.deserialize(map("severityLevels")).split(":").toList.map(SeverityLevel(_).getOrElse(SeverityLevel.Indeterminate))
    val probableCause = formatter.deserialize(map("probableCause"))
    val operatorResponse = formatter.deserialize(map("operatorResponse"))
    val acknowledge = formatter.deserialize(map("acknowledge")).toBoolean
    val latched = formatter.deserialize(map("latched")).toBoolean

    AlarmModel(subsystem, component, name, description, location, alarmType, severityLevels, probableCause,
      operatorResponse, acknowledge, latched)
  }
}

