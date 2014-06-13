package csw.util.cfg

import spray.json._
import spray.httpx.marshalling.MetaMarshallers
import spray.httpx.SprayJsonSupport
import csw.util.cfg.Configurations.{CV, SetupConfig}
import csw.util.cfg.ConfigValues.{CValue, ValueData}
import csw.util.cfg.UnitsOfMeasure.Units

/**
 * Defines JSON marshallers/unmarshallers for SetupConfig, (XXX TODO: ObserveConfig, WaitConfig)
 */
trait ConfigJsonFormats extends DefaultJsonProtocol with SprayJsonSupport with MetaMarshallers {

  implicit object SetupConfigJsonFormat extends RootJsonFormat[SetupConfig] {

    import csw.util.cfg.UnitsOfMeasure.NoUnits

    // -- write --

    private def valueToJsValue[A](value: A): JsValue = value match {
      case Nil => JsNull
      case s: String => JsString(s)
      case i: Int => JsNumber(i)
      case l: Long => JsNumber(l)
      case f: Float => JsNumber(f)
      case d: Double => JsNumber(d)
      case x: Byte => JsNumber(x)
      case x: Short => JsNumber(x)
      case x: BigInt => JsNumber(x)
      case x: BigDecimal => JsNumber(x)
      case c: Char => JsString(String.valueOf(c))
      case s: Seq[A] => JsArray(s.map(valueToJsValue(_)).toList)
      case (a, b) => valueToJsValue(Seq(a, b))
      case (a, b, c) => valueToJsValue(Seq(a, b, c))
      case (a, b, c, d) => valueToJsValue(Seq(a, b, c, d))
      case (a, b, c, d, e) => valueToJsValue(Seq(a, b, c, d, e))
    }

    private def seqToJsValue[A](values: Seq[A]): JsValue = values match {
      case Nil => JsNull
      case head :: Nil => valueToJsValue(head)
      case head :: tail => JsArray(values.map(valueToJsValue(_)).toList)
    }

    private def valueDataToJsValue[A](data: ValueData[A]): JsValue = data.units match {
      case NoUnits => seqToJsValue(data.elems)
      case units => JsObject(("value", seqToJsValue(data.elems)), ("units", JsString(units.name)))
    }

    def write(sc: SetupConfig): JsValue = {
      val items = for (v <- sc.values) yield (v.name, valueDataToJsValue(v.data))
      JsObject(("config", JsObject(
        ("obsId", JsString(sc.obsId)),
        (sc.prefix, JsObject(items.toList)))))
    }

    // -- read --
    private def JsValueToUnits(js: JsValue): Units = js match {
      case JsString(s) => Units.fromString(s)
      case _ => NoUnits
    }

    private def JsValueToValue(js: JsValue): Any = js match {
      case JsString(s) => s
      case JsNumber(n) => if (n.isValidInt) n.toInt else n.toDouble // XXX converts 2.0 to 2 ...
      case JsArray(l) => l // only needed if we have lists as values in the sequence
      case JsFalse => false
      case JsTrue => true
      case JsNull => null
      case x => deserializationError(s"Unexpected JsValue: $x")
    }

    private def JsValueToSeq(js: JsValue): Seq[Any] = js match {
      case s: JsString => Seq(JsValueToValue(s))
      case n: JsNumber => Seq(JsValueToValue(n))
      case JsArray(l) => l.map(JsValueToValue)
      case JsFalse => Seq(false)
      case JsTrue => Seq(true)
      case JsNull => Seq(null)
      case x => deserializationError(s"Unexpected JsValue: $x")
    }

    private def JsValueToCValue(name: String, js: JsValue): CV = js match {
      case s: JsString => CValue(name, ValueData(JsValueToSeq(s)))
      case n: JsNumber => CValue(name, ValueData(JsValueToSeq(n)))
      case JsFalse => CValue(name, ValueData(Seq(false)))
      case JsTrue => CValue(name, ValueData(Seq(true)))
      case JsNull => CValue(name, ValueData(Seq(null)))
      case a: JsArray => CValue(name, ValueData(JsValueToSeq(a)))
      case JsObject(fields) =>
        val value = fields("value")
        val units = fields("units")
        CValue(name, ValueData(JsValueToSeq(value), JsValueToUnits(units)))
      case x => deserializationError(s"Unexpected JsValue: $x")
    }

    private def JsValueToValueSet[A](js: JsValue): Set[CV] =
      (for (field <- js.asJsObject.fields) yield JsValueToCValue(field._1, field._2)).toSet

    def read(json: JsValue): SetupConfig = json match {
      case JsObject(root) =>
        root("config") match {
          case JsObject(configFields) =>
            val obsId = configFields("obsId").convertTo[String]
            val prefix = (configFields - "obsId").keys.head.toString
            val values = JsValueToValueSet(configFields(prefix))
            SetupConfig(obsId, prefix, values)
          case x => deserializationError(s"Unexpected JsValue: $x")
        }
      case x => deserializationError(s"Unexpected JsValue: $x")
    }
  }

}

