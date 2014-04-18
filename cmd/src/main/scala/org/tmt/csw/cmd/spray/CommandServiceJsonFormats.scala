package org.tmt.csw.cmd.spray

import spray.json._
import scala.reflect.ClassTag
import spray.httpx.marshalling.MetaMarshallers
import spray.httpx.SprayJsonSupport
import org.tmt.csw.cmd.akka.{RunId, CommandStatus}
import java.util.UUID
import org.tmt.csw.util.Configuration

/**
 * Defines JSON marshallers/unmarshallers for the objects used in REST messages.
 */
trait CommandServiceJsonFormats extends DefaultJsonProtocol with SprayJsonSupport with MetaMarshallers {

  /**
   * Computes ``RootJsonFormat`` for type ``A`` if ``A`` is object
   */
  def jsonObjectFormat[A: ClassTag]: RootJsonFormat[A] = new RootJsonFormat[A] {
    val ct = implicitly[ClassTag[A]]

    def write(obj: A): JsValue = JsObject("value" -> JsString(ct.runtimeClass.getSimpleName))

    def read(json: JsValue): A = ct.runtimeClass.newInstance().asInstanceOf[A]
  }

  /**
   * Instance of RootJsonFormat for Configuration
   */
  implicit object ConfigurationJsonFormat extends RootJsonFormat[Configuration] {
    def write(config: Configuration): JsString = JsString(config.toJson)

    // The unit tests in TestCommandService produce a JsString here, while testing a standalone app with
    // an http client like curl produces a JsObject with the same contents
    def read(value: JsValue): Configuration = value match {
      case JsString(json) => Configuration(json)
      case o: JsObject => Configuration(o.toString())
      case x => deserializationError(s"Expected Configuration as JsString, but got  + ${x.getClass} : $x")
    }
  }

  /**
   * Instance of RootJsonFormat for RunId
   */
  implicit object RunIdJsonFormat extends RootJsonFormat[RunId] {
    def write(runId: RunId): JsValue = JsObject(("runId", JsString(runId.id)))

    def read(json: JsValue): RunId = json match {
      case JsObject(fields) =>
        fields("runId") match {
          case JsString(s) => RunId(UUID.fromString(s))
          case _ => deserializationError("Expected a RunId")
        }
      case _ => deserializationError("Expected a RunId")
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
        ("partiallyDone", JsBoolean(status.partiallyDone))
      )
    }

    // JSON to object
    def read(value: JsValue): CommandStatus =
      value.asJsObject.getFields("name", "runId", "message", "status") match {
        case Seq(JsString(name), JsString(uuid), JsString(message), JsString(status)) =>
          CommandStatus(name, RunId(UUID.fromString(uuid)), message, status)
        case x => deserializationError("Expected CommandStatus as JsObject, but got " + x.getClass)
      }
  }
}
