package csw.services.cmd.spray

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import csw.shared.CommandStatus
import csw.shared.cmd.RunId
import spray.json._
import java.util.UUID

/**
 * Defines JSON marshallers/unmarshallers for the objects used in REST messages.
 */
trait CommandServiceJsonFormats extends DefaultJsonProtocol with SprayJsonSupport {

  /**
   * Instance of RootJsonFormat for RunId
   */
  implicit object RunIdJsonFormat extends RootJsonFormat[RunId] {
    def write(runId: RunId): JsValue = JsObject(("runId", JsString(runId.id)))

    def read(json: JsValue): RunId = json match {
      case JsObject(fields) ⇒
        fields("runId") match {
          case JsString(s) ⇒ RunId(UUID.fromString(s))
          case _           ⇒ deserializationError("Expected a RunId")
        }
      case _ ⇒ deserializationError("Expected a RunId")
    }
  }

  /**
   * Instance of RootJsonFormat for CommandStatus
   */
  implicit object CommandStatusJsonFormat extends RootJsonFormat[CommandStatus] {

    // Object to JSON
    def write(status: CommandStatus): JsValue = {
      JsObject(
        ("name", JsString(status.name)),
        ("runId", JsString(status.runId.id)),
        ("message", JsString(status.message)),
        ("status", JsString(status.partialStatus)),
        ("done", JsBoolean(status.done)),
        ("partiallyDone", JsBoolean(status.partiallyDone)))
    }

    // JSON to object
    def read(value: JsValue): CommandStatus =
      value.asJsObject.getFields("name", "runId", "message", "status") match {
        case Seq(JsString(name), JsString(uuid), JsString(message), JsString(status)) ⇒
          CommandStatus(name, RunId(UUID.fromString(uuid)), message, status)
        case x ⇒ deserializationError("Expected CommandStatus as JsObject, but got " + x.getClass)
      }
  }

}
