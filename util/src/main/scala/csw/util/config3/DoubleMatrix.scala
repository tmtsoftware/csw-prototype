package csw.util.config3

import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsString, JsValue}

/**
 * Implements a 2d array or matrix type for use in items and configurations
 */
sealed trait Matrix[T] {
  val value: Vector[Vector[T]]
}

/**
 * A 2d array of doubles
 */
case class DoubleMatrix(value: Vector[Vector[Double]]) extends Matrix[Double]

/**
 * Since automatic JSON reading doesn't work with generic types, we need to do it manually here.
 */
case object DoubleMatrix extends DefaultJsonProtocol {

  // Name used as type key in JSON, and for registering the reader: Must be unique
  val typeName = "DoubleMatrix"

  /**
   * For JSON read/write
   */
  implicit def format = jsonFormat1(DoubleMatrix.apply)

  /**
   * Creates a GenericItem[Matrix[Double]] from a JSON value (This didn't work with the jsonFormat3 method)
   */
  def reader[T](json: JsValue): GenericItem[DoubleMatrix] = {
    json.asJsObject.getFields("keyName", "value", "units") match {
      case Seq(JsString(keyName), JsArray(v), u) ⇒
        val units = ConfigJSON.unitsFormat.read(u)
        val value = v.map(DoubleMatrix.format.read)
        GenericItem[DoubleMatrix](typeName, keyName, value, units)
      case _ ⇒ throw new DeserializationException("Color expected")
    }
  }

  GenericItem.register(typeName, reader[Double])
}

