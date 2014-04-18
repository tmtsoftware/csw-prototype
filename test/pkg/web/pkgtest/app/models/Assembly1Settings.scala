package models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import Assembly1Settings._
import org.tmt.csw.util.Configuration


object Assembly1Settings {

  // Some telescope position
  case class TelescopePos(c1: String, c2: String, equinox: String)

  // The base position info
  case class BasePos(posName: String, c1: String, c2: String, equinox: String)

  // The response from the command server for a submit
  case class SubmitResponse(runId: String)

  // The response from the command server for a status query
  case class StatusResponse(name: String, runId: String, message: String)

  def defaultSettings = Assembly1Settings(
    BasePos("", "00:00:00", "00:00:00", "J2000"),
    TelescopePos("00:00:00", "00:00:00", "J2000"))


  // Conversion from JSON to BasePos
  implicit val basePosReads = (
    (__ \ "posName").read[String] ~
      (__ \ "c1").read[String] ~
      (__ \ "c2").read[String] ~
      (__ \ "equinox").read[String]
    )(BasePos)

  // Conversion from JSON to TelescopePos
  implicit val telescopePosReads = (
    (__ \ "c1").read[String] ~
      (__ \ "c2").read[String] ~
      (__ \ "equinox").read[String]
    )(TelescopePos)


  // JSON formats
//  implicit val assembly1SettingsFormat = Json.format[Assembly1Settings]
  implicit val basePosFormat = Json.format[BasePos]
  implicit val telescopePosFormat = Json.format[TelescopePos]
  implicit val submitResponseFormat = Json.format[SubmitResponse]
  implicit val statusResponseFormat = Json.format[StatusResponse]

  // Returns an Assembly1Settings object for the given JSON, wrapped in a JsResult
  def fromJson(json: JsValue): JsResult[Assembly1Settings] = {
    for {
      basePos <- Json.fromJson[BasePos](json \ "config" \ "tmt" \ "tel" \ "base" \ "pos" )
      telescopePos <- Json.fromJson[TelescopePos](json \ "config" \ "tmt" \ "tel" \ "ao" \ "pos" \ "one")
    } yield Assembly1Settings(basePos, telescopePos)
  }
}

// Corresponds to the form that is displayed for editing
case class Assembly1Settings(basePos: BasePos, aoPos: TelescopePos) {
  // Returns the configuration corresponding to this object (in HOCON format:
  // See https://github.com/typesafehub/config/blob/master/HOCON.md
  // This can be converted to JSON by calling toJson.
  // We can't use the usual methods to create JSON in Play, since we need keys with dots in them,
  // but also want them to be part of the hierarchy, which does not work with plain JSON.
  def getConfig: Configuration = Configuration(
    Map("config" ->
      Map(
        "info" -> Map("obsId" -> "TMT-2021A-C-2-1"), // Hard coded for this test
        "tmt.tel.base.pos" -> Map(
          "posName" -> basePos.posName,
          "c1" -> basePos.c1,
          "c2" -> basePos.c2,
          "equinox" -> basePos.equinox),
        "tmt.tel.ao.pos.one" -> Map(
          "c1" -> aoPos.c1,
          "c2" -> aoPos.c2,
          "equinox" -> aoPos.equinox)
      )
    )
  )
}


