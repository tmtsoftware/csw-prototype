package csw.util.config3

import csw.util.config3.Configurations.{ConfigData, ConfigKey, SetupConfig}
import csw.util.config3.UnitsOfMeasure.Units
import spray.json._

/**
 * TMT Source Code: 5/10/16.
 */
object ConfigJSON extends DefaultJsonProtocol {

  //  // Java types
  //  implicit object IntegerJsonFormat extends JsonFormat[Integer] {
  //    def write(x: Integer) = JsNumber(x)
  //    def read(value: JsValue) = value match {
  //      case JsNumber(x) => x.intValue
  //      case x => deserializationError("Expected Int as JsNumber, but got " + x)
  //    }
  //  }
  //

  implicit val unitsFormat = jsonFormat1(Units.apply)

  //  implicit def genericItemFormat[A: JsonFormat] = jsonFormat3(GenericItem.apply[A])

  implicit val charItemFormat = jsonFormat3(CharItem.apply)
  implicit val shortItemFormat = jsonFormat3(ShortItem.apply)
  implicit val intItemFormat = jsonFormat3(IntItem.apply)
  implicit val longItemFormat = jsonFormat3(LongItem.apply)
  implicit val floatItemFormat = jsonFormat3(FloatItem.apply)
  implicit val doubleItemFormat = jsonFormat3(DoubleItem.apply)
  implicit val booleanItemFormat = jsonFormat3(BooleanItem.apply)
  implicit val stringItemFormat = jsonFormat3(StringItem.apply)

  private val charTpe = "CharItem" // Could be classTag[CharItem].toString
  private val shortTpe = "ShortItem"
  private val integerTpe = "IntItem"
  private val longTpe = "LongItem"
  private val floatTpe = "FloatItem"
  private val doubleTpe = "DoubleItem"
  private val booleanTpe = "BooleanItem"
  private val stringTpe = "StringItem"

  implicit def subsystemFormat: JsonFormat[Subsystem] = new JsonFormat[Subsystem] {
    def write(obj: Subsystem) = JsString(obj.name)

    def read(value: JsValue) = {
      value match {
        case JsString(subsystemStr) ⇒ Subsystem.lookup(subsystemStr) match {
          case Some(subsystem) ⇒ subsystem
          case None            ⇒ Subsystem.BAD
        }
        // With malformed JSON, return BAD
        case _ ⇒ Subsystem.BAD
      }
    }
  }

  // XXX Use JNumber?
  def writeItem(item: Item[_, _]): JsValue = {
    val result: (JsString, JsValue) = item match {
      case ci: CharItem    ⇒ (JsString(charTpe), charItemFormat.write(ci))
      case si: ShortItem   ⇒ (JsString(shortTpe), shortItemFormat.write(si))
      case ii: IntItem     ⇒ (JsString(integerTpe), intItemFormat.write(ii))
      case li: LongItem    ⇒ (JsString(longTpe), longItemFormat.write(li))
      case fi: FloatItem   ⇒ (JsString(floatTpe), floatItemFormat.write(fi))
      case di: DoubleItem  ⇒ (JsString(doubleTpe), doubleItemFormat.write(di))
      case bi: BooleanItem ⇒ (JsString(booleanTpe), booleanItemFormat.write(bi))
      case si: StringItem  ⇒ (JsString(stringTpe), stringItemFormat.write(si))
    }
    JsObject("itemType" → result._1, "item" → result._2)
  }

  def readItemAndType(json: JsValue) = json match {
    case JsObject(fields) ⇒
      (fields("itemType"), fields("item")) match {
        case (JsString(`charTpe`), item)    ⇒ charItemFormat.read(item)
        case (JsString(`shortTpe`), item)   ⇒ shortItemFormat.read(item)
        case (JsString(`integerTpe`), item) ⇒ intItemFormat.read(item)
        case (JsString(`longTpe`), item)    ⇒ longItemFormat.read(item)
        case (JsString(`floatTpe`), item)   ⇒ floatItemFormat.read(item)
        case (JsString(`doubleTpe`), item)  ⇒ doubleItemFormat.read(item)
        case (JsString(`booleanTpe`), item) ⇒ booleanItemFormat.read(item)
        case (JsString(`stringTpe`), item)  ⇒ stringItemFormat.read(item)
        case _                              ⇒ ConfigJsonFormats.unexpectedJsValueError(json)
      }
    case _ ⇒ ConfigJsonFormats.unexpectedJsValueError(json)
  }

  implicit def itemsFormat: JsonFormat[ConfigData] = new JsonFormat[ConfigData] {
    def write(items: ConfigData) = JsArray(items.map(writeItem(_)).toList: _*)

    def read(json: JsValue) = json match {
      case a: JsArray ⇒ a.elements.map((el: JsValue) ⇒ readItemAndType(el)).toSet
      case _          ⇒ ConfigJsonFormats.unexpectedJsValueError(json)
    }
  }

  implicit val configKeyFormat = jsonFormat2(ConfigKey.apply)

  def writeConfig[A](cfg: A): JsValue = cfg match {
    case sc: SetupConfig ⇒
      JsObject(
        "configType" → JsString(ConfigJsonFormats.SETUP),
        "configKey" → configKeyFormat.write(sc.configKey),
        "items" → sc.items.toJson
      )
  }

  def readConfig[A](json: JsValue) = json match {
    case JsObject(fields) ⇒
      (fields("configType"), fields("configKey"), fields("items")) match {
        case (JsString(ConfigJsonFormats.SETUP), configKey, items) ⇒
          val ck = configKey.convertTo[ConfigKey]
          SetupConfig(ck, itemsFormat.read(items))
        case _ ⇒ ConfigJsonFormats.unexpectedJsValueError(json)
      }
    case _ ⇒ ConfigJsonFormats.unexpectedJsValueError(json)
  }
}

// Common functions
private object ConfigJsonFormats {
  // roots
  val SETUP = "setup"
  val OBSERVE = "observe"
  val WAIT = "wait"
  val CONFIGS = "configs"

  // Reserved keys
  val OBS_ID = "obsId"

  val reserved = Set(OBS_ID)

  // -- write --

  def valueToJsValue[A](value: A): JsValue = value match { // XXX FIXME
    case Nil                  ⇒ JsNull
    case s: String            ⇒ JsString(s)
    case i: Int               ⇒ JsNumber(i)
    case i: Integer           ⇒ JsNumber(i)
    case l: Long              ⇒ JsNumber(l)
    case f: Float             ⇒ JsNumber(f)
    case d: Double            ⇒ JsNumber(d)
    case x: Byte              ⇒ JsNumber(x)
    case x: Short             ⇒ JsNumber(x)
    case x: BigInt            ⇒ JsNumber(x)
    case x: BigDecimal        ⇒ JsNumber(x)
    case c: Char              ⇒ JsString(String.valueOf(c))
    case s: Seq[A @unchecked] ⇒ JsArray(s.map(valueToJsValue).toList: _*)
    case (a, b)               ⇒ valueToJsValue(Seq(a, b))
    case (a, b, c)            ⇒ valueToJsValue(Seq(a, b, c))
    case (a, b, c, d)         ⇒ valueToJsValue(Seq(a, b, c, d))
    case (a, b, c, d, e)      ⇒ valueToJsValue(Seq(a, b, c, d, e))
  }

  def seqToJsValue[A](values: Seq[A]): JsValue = values.size match {
    case 0 ⇒ JsNull
    case 1 ⇒ valueToJsValue(values.head)
    case _ ⇒ JsArray(values.map(valueToJsValue).toList: _*)
  }

  //  // If the sequence contains elements of non default types (String, Double), Some(typeName)
  //  def typeOption[A](elems: Seq[A]): Option[String] = {
  //    if (elems.isEmpty) None
  //    else elems.head match {
  //      case _: String ⇒ None
  //      case _: Double ⇒ None
  //      case _: Int    ⇒ Some("Int")
  //      case _         ⇒ None
  //    }
  //  }

  // -- read --
  def unexpectedJsValueError(x: JsValue) = deserializationError(s"Unexpected JsValue: $x")

  //  def JsValueToValue(js: JsValue, typeOpt: Option[String] = None): Any = js match {
  //    case JsString(s) ⇒ s
  //    case JsNumber(n) ⇒
  //      typeOpt match {
  //        case Some("Int")   ⇒ n.toInt
  //        case Some("Short") ⇒ n.toShort
  //        case Some("Long")  ⇒ n.toLong
  //        case Some("Byte")  ⇒ n.toByte
  //        case _             ⇒ n.toDouble
  //      }
  //    case JsArray(l) ⇒ l // only needed if we have lists as values in the sequence
  //    case JsFalse    ⇒ false
  //    case JsTrue     ⇒ true
  //    case JsNull     ⇒ null
  //    case x          ⇒ unexpectedJsValueError(x)
  //  }

  /*

  def JsValueToSeq(js: JsValue, typeOpt: Option[String] = None): Seq[Any] = js match {
    case s: JsString ⇒ Seq(JsValueToValue(s))
    case n: JsNumber ⇒ Seq(JsValueToValue(n, typeOpt))
    case JsArray(l)  ⇒ l.map(JsValueToValue(_, typeOpt))
    case JsFalse     ⇒ Seq(false)
    case JsTrue      ⇒ Seq(true)
    case JsNull      ⇒ Seq(null)
    case x           ⇒ unexpectedJsValueError(x)
  }

  def JsValueToCValue(name: String, js: JsValue): CV = js match {
    case s: JsString ⇒ CValue(name, ValueData(JsValueToSeq(s)))
    case n: JsNumber ⇒ CValue(name, ValueData(JsValueToSeq(n)))
    case JsFalse     ⇒ CValue(name, ValueData(Seq(false)))
    case JsTrue      ⇒ CValue(name, ValueData(Seq(true)))
    case JsNull      ⇒ CValue(name, ValueData(Seq(null)))
    case a: JsArray  ⇒ CValue(name, ValueData(JsValueToSeq(a)))
    case JsObject(fields) ⇒
      val units = fields.get("units") match {
        case None          ⇒ NoUnits
        case Some(jsValue) ⇒ JsValueToUnits(jsValue)
      }
      val typeOpt = fields.get("type") match {
        case None                     ⇒ None
        case Some(JsString(typeName)) ⇒ Some(typeName)
        case Some(x)                  ⇒ None // Should not happen
      }
      val value = JsValueToSeq(fields("value"), typeOpt)
      CValue(name, ValueData(value, units))
    case x ⇒ unexpectedJsValueError(x)
  }

  def JsValueToValueSet[A](js: JsValue): Set[CV] =
    (for (field ← js.asJsObject.fields) yield JsValueToCValue(field._1, field._2)).toSet
    */
}
