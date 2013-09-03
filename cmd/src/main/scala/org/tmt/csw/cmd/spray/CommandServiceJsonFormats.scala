package org.tmt.csw.cmd.spray

import spray.json._
import scala.reflect.ClassTag
import spray.httpx.marshalling.MetaMarshallers
import spray.httpx.SprayJsonSupport
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka.{RunId, CommandStatus}
import java.util.UUID

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
    def write(config: Configuration): JsString = {
      val json = JsString(config.toJson)
      println(s"XXX json for config = $json")
      json
    }

    // XXX FIXME
    def read(value: JsValue): Configuration = value match {
      case JsString(json) =>
        println(s"XXX ConfigurationJsonFormat: value = $value")
        val config = Configuration(json)
        println(s"XXX ConfigurationJsonFormat: config = $config")
        config
      case o: JsObject =>
        val json = o.toString()
        println(s"XXX ConfigurationJsonFormat: value = $value")
        val config = Configuration(json)
        println(s"XXX ConfigurationJsonFormat: config = $config")
        config

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
          case JsString(s) => {
            println(s"XXX got s = $s")
            RunId(UUID.fromString(s))
          }
          case _ => deserializationError("Expected a RunId")
        }
      case _ => deserializationError("Expected a RunId")
    }
  }

  /**
   * Instance of RootJsonFormat for CommandStatus
   */
  implicit object CommandStatusJsonFormat extends RootJsonFormat[CommandStatus] {
    def write(status: CommandStatus): JsValue = JsObject(
      ("name", JsString(status.getClass.getSimpleName)),
      ("runId", JsString(status.runId.id)),
      ("message", JsString(status.message))
    )

    def read(value: JsValue): CommandStatus =
      value.asJsObject.getFields("name", "runId", "message") match {
        case Seq(JsString(name), JsString(uuid), JsString(message)) =>
          CommandStatus(name, RunId(UUID.fromString(uuid)), message)
        case x => deserializationError("Expected CommandStatus as JsObject, but got " + x.getClass)
      }
  }

  //  /**
  //   * Type alias for function that converts ``A`` to some ``StatusCode``
  //   * @tparam A the type of the input values
  //   */
  //  type ErrorSelector[A] = A => StatusCode
  //
  //  /**
  //   * Marshals instances of ``Either[A, B]`` into appropriate HTTP responses by marshalling the values
  //   * in the left or right projections; and by selecting the appropriate HTTP status code for the
  //   * values in the left projection.
  //   *
  //   * @param ma marshaller for the left projection
  //   * @param mb marshaller for the right projection
  //   * @param esa the selector converting the left projection to HTTP status code
  //   * @tparam A the left projection
  //   * @tparam B the right projection
  //   * @return marshaller
  //   */
  //  implicit def errorSelectingEitherMarshaller[A, B](implicit ma: Marshaller[A], mb: Marshaller[B], esa: ErrorSelector[A]): Marshaller[Either[A, B]] =
  //    Marshaller[Either[A, B]] {
  //      (value, ctx) =>
  //        value match {
  //          case Left(a) =>
  //            val mc = new CollectingMarshallingContext()
  //            ma(a, mc)
  //            ctx.handleError(ErrorResponseException(esa(a), mc.entity))
  //          case Right(b) =>
  //            mb(b, ctx)
  //        }
  //    }

}
