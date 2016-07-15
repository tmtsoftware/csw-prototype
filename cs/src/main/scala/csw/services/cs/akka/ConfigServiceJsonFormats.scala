package csw.services.cs.akka

import java.io.File
import java.util.Date

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import csw.services.cs.core.{ConfigFileHistory, ConfigFileInfo, ConfigId}
import spray.json._

/**
 * Defines JSON marshallers/unmarshallers for the objects used in HTTP/REST messages.
 */
trait ConfigServiceJsonFormats extends DefaultJsonProtocol with SprayJsonSupport {

  /**
   * ConfigId JSON support
   */
  implicit object ConfigIdJsonFormat extends RootJsonFormat[ConfigId] {
    override def write(configId: ConfigId): JsValue = JsObject(("ConfigId", JsString(configId.id)))

    override def read(json: JsValue): ConfigId = json match {
      case JsObject(fields) =>
        fields("ConfigId") match {
          case JsString(s) => ConfigId(s)
          case _           => deserializationError("Expected a ConfigId")
        }
      case _ => deserializationError("Expected a ConfigId")
    }
  }

  /**
   * ConfigFileInfo JSON support
   */
  implicit object ConfigFileInfoJsonFormat extends RootJsonFormat[ConfigFileInfo] {
    override def write(info: ConfigFileInfo): JsValue = {
      JsObject(
        ("path", JsString(info.path.toString)),
        ("id", JsString(info.id.id)),
        ("comment", JsString(info.comment))
      )
    }

    override def read(value: JsValue): ConfigFileInfo =
      value.asJsObject.getFields("path", "id", "comment") match {
        case Seq(JsString(path), JsString(id), JsString(comment)) =>
          ConfigFileInfo(new File(path), ConfigId(id), comment)
        case x => deserializationError("Expected ConfigFileInfo as JsObject, but got " + x.getClass)
      }
  }

  /**
   * ConfigFileHistory JSON support
   */
  implicit object ConfigFileHistoryJsonFormat extends RootJsonFormat[ConfigFileHistory] {
    override def write(h: ConfigFileHistory): JsValue = {
      JsObject(
        ("id", JsString(h.id.id)),
        ("comment", JsString(h.comment)),
        ("time", JsNumber(h.time.getTime))
      )
    }

    override def read(value: JsValue): ConfigFileHistory =
      value.asJsObject.getFields("id", "comment", "time") match {
        case Seq(JsString(id), JsString(comment), JsNumber(time)) =>
          ConfigFileHistory(ConfigId(id), comment, new Date(time.longValue()))
        case x => deserializationError("Expected ConfigFileHistory as JsObject, but got " + x.getClass)
      }
  }
}
