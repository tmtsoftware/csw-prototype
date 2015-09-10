package csw.util.config

//import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
//import csw.util.config.Configurations._
//import spray.json._
//
///**
// * Defines JSON marshallers/unmarshallers for config classes.
// * This adds toJson and parseJson methods to the config classes.
// *
// * (Note: upickle automatic conversion failed here due to limitations
// * (http://lihaoyi.github.io/upickle-pprint/upickle/#Limitations))
// */
//trait ConfigJsonFormats extends DefaultJsonProtocol with SprayJsonSupport {
//
//  import ConfigJsonFormats._
//
//  /**
//   * JSON I/O for SetupConfig
//   */
//  implicit object SetupConfigJsonFormat extends RootJsonFormat[SetupConfig] {
//
//    def write(sc: SetupConfig): JsValue = {
//      val items = for ((k, v) <- sc.data.data) yield k.name -> valueToJsValue(v)
//      JsObject(SETUP -> JsObject(
//        sc.configKey.path -> JsObject(items)))
//    }
//
//    def read(json: JsValue): SetupConfig = json match {
//      case JsObject(root) ⇒
//        root(SETUP) match {
//          case JsObject(configFields) ⇒
//            val path = configFields.keys.head
//            SetupConfig(ConfigKey(path), JsValueToValueSet(configFields(path)))
//          case x ⇒ unexpectedJsValueError(x)
//        }
//      case x ⇒ unexpectedJsValueError(x)
//    }
//  }
//
//  /**
//   * JSON I/O for ObserveConfig
//   */
//  implicit object ObserveConfigJsonFormat extends RootJsonFormat[ObserveConfig] {
//
//    def write(oc: ObserveConfig): JsValue = {
//      JsObject((OBSERVE, JsObject(
//        (OBS_ID, JsString(oc.obsId)))))
//    }
//
//    def read(json: JsValue): ObserveConfig = json match {
//      case JsObject(root) ⇒
//        root(OBSERVE) match {
//          case JsObject(configFields) ⇒
//            val obsId = configFields(OBS_ID).convertTo[String]
//            ObserveConfig(obsId)
//          case x ⇒ unexpectedJsValueError(x)
//        }
//      case x ⇒ unexpectedJsValueError(x)
//    }
//  }
//
//  /**
//   * JSON I/O for WaitConfig
//   */
//  implicit object WaitConfigJsonFormat extends RootJsonFormat[WaitConfig] {
//
//    def write(wc: WaitConfig): JsValue = {
//      JsObject((WAIT, JsObject(
//        (OBS_ID, JsString(wc.obsId)))))
//    }
//
//    def read(json: JsValue): WaitConfig = json match {
//      case JsObject(root) ⇒
//        root(WAIT) match {
//          case JsObject(configFields) ⇒
//            val obsId = configFields(OBS_ID).convertTo[String]
//            WaitConfig(obsId)
//          case x ⇒ unexpectedJsValueError(x)
//        }
//      case x ⇒ unexpectedJsValueError(x)
//    }
//  }
//
//  /**
//   * JSON I/O for ConfigType trait
//   */
//  implicit object ConfigTypeJsonFormat extends RootJsonFormat[ConfigType] {
//
//    def write(ct: ConfigType): JsValue = ct match {
//      case sc: SetupConfig ⇒ SetupConfigJsonFormat.write(sc)
//      case oc: ObserveConfig ⇒ ObserveConfigJsonFormat.write(oc)
//      case wc: WaitConfig ⇒ WaitConfigJsonFormat.write(wc)
//    }
//
//    def read(json: JsValue): ConfigType = json match {
//      case JsObject(root) ⇒
//        root.keys.head match {
//          case SETUP ⇒ SetupConfigJsonFormat.read(json)
//          case OBSERVE ⇒ ObserveConfigJsonFormat.read(json)
//          case WAIT ⇒ WaitConfigJsonFormat.read(json)
//          case x ⇒ deserializationError(s"Unexpected config type: $x")
//        }
//      case x ⇒ unexpectedJsValueError(x)
//    }
//  }
//
//  /**
//   * JSON I/O for SetupConfigList.
//   * This adds toJson and parseJson methods to the SetupConfigList type (list[SetupConfig]).
//   */
//  implicit object SetupConfigListJsonFormat extends RootJsonFormat[SetupConfigList] {
//    def write(configs: SetupConfigList): JsValue = {
//      val items = JsArray(configs.map(_.toJson): _*)
//      JsObject(CONFIGS -> items)
//    }
//
//    def read(json: JsValue): SetupConfigList = json match {
//      case JsObject(root) ⇒
//        root(CONFIGS) match {
//          case JsArray(values) ⇒ values.toList.map(SetupConfigJsonFormat.read)
//          case x ⇒ deserializationError(s"Unexpected JsValue: $x")
//        }
//      case x ⇒ deserializationError(s"Unexpected JsValue: $x")
//    }
//  }
//
//  /**
//   * JSON I/O for ConfigList objects.
//   * This adds toJson and parseJson methods to ConfigList (List[ConfigType]).
//   */
//  implicit object ConfigListJsonFormat extends RootJsonFormat[List[ConfigType]] {
//    def write(configs: List[ConfigType]): JsValue = {
//      val items = JsArray(configs.map(_.toJson): _*)
//      JsObject(CONFIGS -> items)
//    }
//
//    def read(json: JsValue): List[ConfigType] = json match {
//      case JsObject(root) ⇒
//        root(CONFIGS) match {
//          case JsArray(values) ⇒ values.toList.map(ConfigTypeJsonFormat.read)
//          case x ⇒ deserializationError(s"Unexpected JsValue: $x")
//        }
//      case x ⇒ deserializationError(s"Unexpected JsValue: $x")
//    }
//  }
//
//}
//
//// Common functions
//private object ConfigJsonFormats {
//
//  import csw.util.cfg.UnitsOfMeasure.NoUnits
//
//  // roots
//  val SETUP = "setup"
//  val OBSERVE = "observe"
//  val WAIT = "wait"
//  val CONFIGS = "configs"
//
//  // Reserved keys
//  val OBS_ID = "obsId"
//
//  val reserved = Set(OBS_ID)
//
//  // -- write --
//
//  def valueToJsValue[A](value: A): JsValue = value match {
//    // XXX FIXME
//    case Nil ⇒ JsNull
//    case s: String ⇒ JsString(s)
//    case i: Int ⇒ JsNumber(i)
//    case i: Integer ⇒ JsNumber(i)
//    case l: Long ⇒ JsNumber(l)
//    case f: Float ⇒ JsNumber(f)
//    case d: Double ⇒ JsNumber(d)
//    case x: Byte ⇒ JsNumber(x)
//    case x: Short ⇒ JsNumber(x)
//    case x: BigInt ⇒ JsNumber(x)
//    case x: BigDecimal ⇒ JsNumber(x)
//    case c: Char ⇒ JsString(String.valueOf(c))
//    case s: Seq[A@unchecked] ⇒ JsArray(s.map(valueToJsValue).toList: _*)
//    case (a, b) ⇒ valueToJsValue(Seq(a, b))
//    case (a, b, c) ⇒ valueToJsValue(Seq(a, b, c))
//    case (a, b, c, d) ⇒ valueToJsValue(Seq(a, b, c, d))
//    case (a, b, c, d, e) ⇒ valueToJsValue(Seq(a, b, c, d, e))
//  }
//
//  def seqToJsValue[A](values: Seq[A]): JsValue = values.size match {
//    case 0 ⇒ JsNull
//    case 1 ⇒ valueToJsValue(values.head)
//    case _ ⇒ JsArray(values.map(valueToJsValue).toList: _*)
//  }
//
//  // If the sequence contains elements of non default types (String, Double), Some(typeName)
//  def typeOption[A](elems: Seq[A]): Option[String] = {
//    if (elems.isEmpty) None
//    else elems.head match {
//      case _: String ⇒ None
//      case _: Double ⇒ None
//      case _: Int ⇒ Some("Int")
//      case _ ⇒ None
//    }
//  }
//
//  def valueDataToJsValue[A](data: ValueData[A]): JsValue = data.units match {
//    case NoUnits ⇒
//      typeOption(data.elems) match {
//        case None ⇒
//          seqToJsValue(data.elems)
//        case Some(typeName) ⇒
//          JsObject(("value", seqToJsValue(data.elems)), ("type", JsString(typeName)))
//      }
//    case units ⇒
//      typeOption(data.elems) match {
//        case None ⇒
//          JsObject(("value", seqToJsValue(data.elems)), ("units", JsString(units.name)))
//        case Some(typeName) ⇒
//          JsObject(("value", seqToJsValue(data.elems)), ("units", JsString(units.name)), ("type", JsString(typeName)))
//      }
//  }
//
//  // -- read --
//  def unexpectedJsValueError(x: JsValue) = deserializationError(s"Unexpected JsValue: $x")
//
//  def JsValueToUnits(js: JsValue): Units = js match {
//    case JsString(s) ⇒ Units.fromString(s)
//    case _ ⇒ NoUnits
//  }
//
//  def JsValueToValue(js: JsValue, typeOpt: Option[String] = None): Any = js match {
//    case JsString(s) ⇒ s
//    case JsNumber(n) ⇒
//      typeOpt match {
//        case Some("Int") ⇒ n.toInt
//        case Some("Short") ⇒ n.toShort
//        case Some("Long") ⇒ n.toLong
//        case Some("Byte") ⇒ n.toByte
//        case _ ⇒ n.toDouble
//      }
//    case JsArray(l) ⇒ l // only needed if we have lists as values in the sequence
//    case JsFalse ⇒ false
//    case JsTrue ⇒ true
//    case JsNull ⇒ null
//    case x ⇒ unexpectedJsValueError(x)
//  }
//
//  def JsValueToSeq(js: JsValue, typeOpt: Option[String] = None): Seq[Any] = js match {
//    case s: JsString ⇒ Seq(JsValueToValue(s))
//    case n: JsNumber ⇒ Seq(JsValueToValue(n, typeOpt))
//    case JsArray(l) ⇒ l.map(JsValueToValue(_, typeOpt))
//    case JsFalse ⇒ Seq(false)
//    case JsTrue ⇒ Seq(true)
//    case JsNull ⇒ Seq(null)
//    case x ⇒ unexpectedJsValueError(x)
//  }
//
//  def JsValueToCValue(name: String, js: JsValue): CV = js match {
//    case s: JsString ⇒ CValue(name, ValueData(JsValueToSeq(s)))
//    case n: JsNumber ⇒ CValue(name, ValueData(JsValueToSeq(n)))
//    case JsFalse ⇒ CValue(name, ValueData(Seq(false)))
//    case JsTrue ⇒ CValue(name, ValueData(Seq(true)))
//    case JsNull ⇒ CValue(name, ValueData(Seq(null)))
//    case a: JsArray ⇒ CValue(name, ValueData(JsValueToSeq(a)))
//    case JsObject(fields) ⇒
//      val units = fields.get("units") match {
//        case None ⇒ NoUnits
//        case Some(jsValue) ⇒ JsValueToUnits(jsValue)
//      }
//      val typeOpt = fields.get("type") match {
//        case None ⇒ None
//        case Some(JsString(typeName)) ⇒ Some(typeName)
//        case Some(x) ⇒ None // Should not happen
//      }
//      val value = JsValueToSeq(fields("value"), typeOpt)
//      CValue(name, ValueData(value, units))
//    case x ⇒ unexpectedJsValueError(x)
//  }
//
//  def JsValueToValueSet[A](js: JsValue): Set[CV] =
//    (for (field ← js.asJsObject.fields) yield JsValueToCValue(field._1, field._2)).toSet
//}
//
