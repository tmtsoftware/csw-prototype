package csw.services.alarms

import csw.services.alarms.AlarmModel.{AlarmType, SeverityLevel}
import spray.json._

/**
 * Supports JSON I/O for AlarmModel
 */
object AlarmJson extends DefaultJsonProtocol {

  def toJson(a: AlarmModel): JsValue = a.asMap().toJson

  def fromJson(json: JsValue): AlarmModel = {
    import AlarmModel.F
    val map = mapFormat[String, String].read(json)

    val subsystem = map(F.subsystem)
    val component = map(F.component)
    val name = map(F.name)
    val description = map(F.description)
    val location = map(F.location)
    val alarmType = AlarmType(map(F.alarmType))
    val severityLevels = map(F.severityLevels).split(":").toList.map(SeverityLevel(_).getOrElse(SeverityLevel.Disconnected))
    val probableCause = map(F.probableCause)
    val operatorResponse = map(F.operatorResponse)
    val acknowledge = map(F.acknowledge).toBoolean
    val latched = map(F.latched).toBoolean

    AlarmModel(subsystem, component, name, description, location, alarmType, severityLevels, probableCause,
      operatorResponse, acknowledge, latched)
  }
}
