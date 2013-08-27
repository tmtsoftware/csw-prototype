package org.tmt.csw.cs.spray

import spray.json._
import scala.reflect.ClassTag
import spray.httpx.marshalling.{MetaMarshallers, Marshaller, CollectingMarshallingContext}
import spray.http.StatusCode
import spray.httpx.SprayJsonSupport
import org.tmt.csw.cs.spray.services.ErrorResponseException
import java.io.File
import org.tmt.csw.cs.api.{ConfigId, ConfigData}
import org.tmt.csw.cs.core.{GitConfigId, ConfigString}

/**
 * Contains useful JSON formats: ``j.u.Date``, ``j.u.UUID`` and others; it is useful
 * when creating traits that contain the ``JsonReader`` and ``JsonWriter`` instances
 * for types that contain ``Date``s, ``UUID``s and such like.
 */
trait DefaultJsonFormats extends DefaultJsonProtocol with SprayJsonSupport with MetaMarshallers {

  /**
   * Computes ``RootJsonFormat`` for type ``A`` if ``A`` is object
   */
  def jsonObjectFormat[A : ClassTag]: RootJsonFormat[A] = new RootJsonFormat[A] {
    val ct = implicitly[ClassTag[A]]
    def write(obj: A): JsValue = JsObject("value" -> JsString(ct.runtimeClass.getSimpleName))
    def read(json: JsValue): A = ct.runtimeClass.newInstance().asInstanceOf[A]
  }

  /**
   * Instance of RootJsonFormat for java.io.File
   */
  implicit object FileJsonFormat extends RootJsonFormat[File] {
    def write(f: File): JsString = JsString(f.toString)
    def read(value: JsValue): File = value match {
      case JsString(f) => new File(f)
      case f           => deserializationError("Expected File as JsString, but got " + f)
    }
  }

  /**
   * Instance of RootJsonFormat for ConfigData
   */
  implicit object ConfigDataJsonFormat extends RootJsonFormat[ConfigData] {
    // XXX TODO FIXME (might be binary or different charset)
    def write(configData: ConfigData): JsString = JsString(new String(configData.getBytes))
    def read(value: JsValue): ConfigData = value match {
      case JsString(s) => ConfigString(s)
      case x           => deserializationError("Expected ConfigData as JsString, but got " + x)
    }
  }

  /**
   * Instance of RootJsonFormat for ConfigId
   */
  implicit object ConfigIdJsonFormat extends RootJsonFormat[ConfigId] {
    def write(configId: ConfigId): JsString = JsString(configId.id)
    def read(value: JsValue): ConfigId = value match {
      case JsString(s) => GitConfigId(s)
      case x           => deserializationError("Expected ConfigId as JsString, but got " + x)
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
    Marshaller[Either[A, B]] { (value, ctx) =>
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
