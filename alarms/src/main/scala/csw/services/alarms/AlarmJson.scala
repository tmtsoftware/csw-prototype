package csw.services.alarms

import csw.services.alarms.AlarmModel.{AlarmType, SeverityLevel}
import spray.json._

/**
 * Supports JSON I/O for AlarmModel
 */
object AlarmJson extends DefaultJsonProtocol {

  def toJson(a: AlarmModel): JsValue = a.asMap().toJson

  def fromJson(json: JsValue): AlarmModel = {
    val map = mapFormat[String, String].read(json)

    val subsystem = map("subsystem")
    val component = map("component")
    val name = map("name")
    val description = map("description")
    val location = map("location")
    val alarmType = AlarmType(map("alarmType"))
    val severityLevels = map("severityLevels").split(":").toList.map(SeverityLevel(_).getOrElse(SeverityLevel.Indeterminate))
    val probableCause = map("probableCause")
    val operatorResponse = map("operatorResponse")
    val acknowledge = map("acknowledge").toBoolean
    val latched = map("latched").toBoolean

    AlarmModel(subsystem, component, name, description, location, alarmType, severityLevels, probableCause,
      operatorResponse, acknowledge, latched)
  }
}
