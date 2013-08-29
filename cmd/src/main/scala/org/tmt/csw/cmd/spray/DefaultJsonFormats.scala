package org.tmt.csw.cmd.spray

import spray.json._
import scala.reflect.ClassTag
import spray.httpx.marshalling.{MetaMarshallers, Marshaller, CollectingMarshallingContext}
import spray.http.StatusCode
import spray.httpx.SprayJsonSupport
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka.{RunId, CommandStatus}
import java.util.UUID

/**
 * Contains useful JSON formats: ``j.u.Date``, ``j.u.UUID`` and others; it is useful
 * when creating traits that contain the ``JsonReader`` and ``JsonWriter`` instances
 * for types that contain ``Date``s, ``UUID``s and such like.
 */
trait DefaultJsonFormats extends DefaultJsonProtocol with SprayJsonSupport with MetaMarshallers {

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

    def read(value: JsValue): Configuration = value match {
      case obj: JsObject => Configuration(obj.toString())
      case x => deserializationError("Expected Configuration as JsObject, but got " + x.getClass)
    }
  }

  /**
   * Instance of RootJsonFormat for CommandStatus
   */
  implicit object CommandStatusJsonFormat extends RootJsonFormat[CommandStatus] {
    def write(status: CommandStatus): JsValue = JsObject(("status", JsString(status.getClass.getSimpleName)))

    def read(value: JsValue): CommandStatus = deserializationError("Operation not supported on CommandStatus")
  }

  /**
   * Instance of RootJsonFormat for RunId
   */
  implicit object RunIdJsonFormat extends RootJsonFormat[RunId] {
    def write(runId: RunId): JsValue = JsObject(("runId", JsString(runId.id)))

    def read(json: JsValue): RunId = json match {
      case JsObject(fields) =>
        fields("runId") match {
          case s: JsString => RunId(UUID.fromString(s.toString()))
          case _ => deserializationError("Expected a RunId")
        }
      case _ => deserializationError("Expected a RunId")
    }
  }

  /**
   * Type alias for function that converts ``A`` to some ``StatusCode``
   * @tparam A the type of the input values
   */
  type ErrorSelector[A] = A => StatusCode

  /**
   * Marshals instances of ``Either[A, B]`` into appropriate HTTP responses by marshalling the values
   * in the left or right projections; and by selecting the appropriate HTTP status code for the
   * values in the left projection.
   *
   * @param ma marshaller for the left projection
   * @param mb marshaller for the right projection
   * @param esa the selector converting the left projection to HTTP status code
   * @tparam A the left projection
   * @tparam B the right projection
   * @return marshaller
   */
  implicit def errorSelectingEitherMarshaller[A, B](implicit ma: Marshaller[A], mb: Marshaller[B], esa: ErrorSelector[A]): Marshaller[Either[A, B]] =
    Marshaller[Either[A, B]] {
      (value, ctx) =>
        value match {
          case Left(a) =>
            val mc = new CollectingMarshallingContext()
            ma(a, mc)
            ctx.handleError(ErrorResponseException(esa(a), mc.entity))
          case Right(b) =>
            mb(b, ctx)
        }
    }

}
