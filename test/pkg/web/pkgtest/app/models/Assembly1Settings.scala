package models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import Assembly1Settings._


object Assembly1Settings {

  // JSON formats
  // XXX: Could also get some of these defs by adding csw/cmd as a dependency...
  implicit val telescopePosFormat = Json.format[TelescopePos]
  implicit val basePosFormat = Json.format[BasePos]
  implicit val submitResponseFormat = Json.format[SubmitResponse]
  implicit val statusResponseFormat = Json.format[StatusResponse]
  implicit val assembly1SettingsFormat = Json.format[Assembly1Settings]
  implicit val assembly1ConfigFormat = Json.format[Assembly1Config]

//  // Override default to change the key names to be like the examples in the TMT specs
//  implicit val assembly1SettingWrites = new Writes[Assembly1Settings] {
//    def writes(settings: Assembly1Settings): JsValue = {
//      Json.obj(
//        "tmt.tel.base.pos" -> settings.basePos,
//        "tmt.tel.ao.pos.one" -> settings.aoPos
//      )
//    }
//  }

  def defaultSettings = Assembly1Settings(BasePos("", "00:00:00", "00:00:00", "J2000"), TelescopePos("00:00:00", "00:00:00", "J2000"))

}

// XXX Replace String with some coordinate class, etc...

// Some telescope position
case class TelescopePos(c1: String, c2: String, equinox: String)

// The base position info
case class BasePos(posName: String, c1: String, c2: String, equinox: String)

// Corresponds to the form that is displayed for editing
case class Assembly1Settings(basePos: BasePos, aoPos: TelescopePos)


// Config object that is submitted to the assembly1 command service
case class Assembly1Config(config: Assembly1Settings)

// The response from the command server for a submit
case class SubmitResponse(runId: String)

// The response from the command server for a status query
case class StatusResponse(name: String, runId: String, message: String)

