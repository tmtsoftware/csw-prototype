package csw.util.config3

import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsString, JsValue}

/**
 * Holds ra and dec values
 */
case class RaDec(ra: Double, dec: Double)

/**
 * Since automatic JSON reading doesn't work with generic types, we need to do it manually here.
 */
case object RaDec extends DefaultJsonProtocol {
  /**
   * JSON read/write for RaDecItem
   */
  implicit val raDecFormat = jsonFormat2(RaDec.apply)

  /**
   * Creates a GenericItem[RaDec] from a JSON value (This didn't work with the jsonFormat3 method)
   */
  def reader(json: JsValue): GenericItem[RaDec] = {
    json.asJsObject.getFields("keyName", "value", "units") match {
      case Seq(JsString(keyName), JsArray(v), u) ⇒
        val units = ConfigJSON.unitsFormat.read(u)
        val value = v.map(RaDec.raDecFormat.read)
        GenericItem[RaDec]("RaDec", keyName, value, units)
      case _ ⇒ throw new DeserializationException("Color expected")
    }
  }

  GenericItem.register("RaDec", reader)
}

