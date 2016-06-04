package csw.util.config

import csw.util.config.Configurations.{ConfigData, ConfigKey, SetupConfig}
import csw.util.config.UnitsOfMeasure.Units
import spray.json._

/**
 * TMT Source Code: 5/10/16.
 */
object ConfigJSON extends DefaultJsonProtocol {
  implicit val unitsFormat = jsonFormat1(Units.apply)

  implicit val charItemFormat = jsonFormat3(CharItem.apply)
  implicit val shortItemFormat = jsonFormat3(ShortItem.apply)
  implicit val intItemFormat = jsonFormat3(IntItem.apply)
  implicit val longItemFormat = jsonFormat3(LongItem.apply)
  implicit val floatItemFormat = jsonFormat3(FloatItem.apply)
  implicit val doubleItemFormat = jsonFormat3(DoubleItem.apply)
  implicit val booleanItemFormat = jsonFormat3(BooleanItem.apply)
  implicit val stringItemFormat = jsonFormat3(StringItem.apply)
  implicit val doubleMatrixItemFormat = jsonFormat3(DoubleMatrixItem.apply)
  implicit val doubleVectorItemFormat = jsonFormat3(DoubleVectorItem.apply)
  implicit val intMatrixItemFormat = jsonFormat3(IntMatrixItem.apply)
  implicit val intVectorItemFormat = jsonFormat3(IntVectorItem.apply)

  private val charTpe = "CharItem"
  // Could be classTag[CharItem].toString
  private val shortTpe = "ShortItem"
  private val integerTpe = "IntItem"
  private val longTpe = "LongItem"
  private val floatTpe = "FloatItem"
  private val doubleTpe = "DoubleItem"
  private val booleanTpe = "BooleanItem"
  private val stringTpe = "StringItem"
  private val doubleMatrixTpe = "DoubleMatrixItem"
  private val doubleVectorTpe = "DoubleVectorItem"
  private val intMatrixTpe = "IntMatrixItem"
  private val intVectorTpe = "IntVectorItem"

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

  // XXX TODO Use JNumber?
  def writeItem[S, J](item: Item[S, J]): JsValue = {
    val result: (JsString, JsValue) = item match {
      case ci: CharItem         ⇒ (JsString(charTpe), charItemFormat.write(ci))
      case si: ShortItem        ⇒ (JsString(shortTpe), shortItemFormat.write(si))
      case ii: IntItem          ⇒ (JsString(integerTpe), intItemFormat.write(ii))
      case li: LongItem         ⇒ (JsString(longTpe), longItemFormat.write(li))
      case fi: FloatItem        ⇒ (JsString(floatTpe), floatItemFormat.write(fi))
      case di: DoubleItem       ⇒ (JsString(doubleTpe), doubleItemFormat.write(di))
      case bi: BooleanItem      ⇒ (JsString(booleanTpe), booleanItemFormat.write(bi))
      case si: StringItem       ⇒ (JsString(stringTpe), stringItemFormat.write(si))
      case di: DoubleMatrixItem ⇒ (JsString(doubleMatrixTpe), doubleMatrixItemFormat.write(di))
      case di: DoubleVectorItem ⇒ (JsString(doubleVectorTpe), doubleVectorItemFormat.write(di))
      case di: IntMatrixItem    ⇒ (JsString(intMatrixTpe), intMatrixItemFormat.write(di))
      case di: IntVectorItem    ⇒ (JsString(intVectorTpe), intVectorItemFormat.write(di))
      case gi: GenericItem[S]   ⇒ (JsString(gi.typeName), gi.toJson)
    }
    JsObject("itemType" → result._1, "item" → result._2)
  }

  def readItemAndType(json: JsValue): Item[_, _] = json match {
    case JsObject(fields) ⇒
      (fields("itemType"), fields("item")) match {
        case (JsString(`charTpe`), item)         ⇒ charItemFormat.read(item)
        case (JsString(`shortTpe`), item)        ⇒ shortItemFormat.read(item)
        case (JsString(`integerTpe`), item)      ⇒ intItemFormat.read(item)
        case (JsString(`longTpe`), item)         ⇒ longItemFormat.read(item)
        case (JsString(`floatTpe`), item)        ⇒ floatItemFormat.read(item)
        case (JsString(`doubleTpe`), item)       ⇒ doubleItemFormat.read(item)
        case (JsString(`booleanTpe`), item)      ⇒ booleanItemFormat.read(item)
        case (JsString(`stringTpe`), item)       ⇒ stringItemFormat.read(item)
        case (JsString(`doubleMatrixTpe`), item) ⇒ doubleMatrixItemFormat.read(item)
        case (JsString(`doubleVectorTpe`), item) ⇒ doubleVectorItemFormat.read(item)
        case (JsString(`intMatrixTpe`), item)    ⇒ intMatrixItemFormat.read(item)
        case (JsString(`intVectorTpe`), item)    ⇒ intVectorItemFormat.read(item)
        case (JsString(typeTag), item) ⇒
          GenericItem.lookup(typeTag) match {
            case Some(jsonReaderFunc) ⇒ jsonReaderFunc(item)
            case None                 ⇒ ConfigJsonFormats.unexpectedJsValueError(item)
          }
        case _ ⇒ ConfigJsonFormats.unexpectedJsValueError(json)
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

  def readConfig[A](json: JsValue): SetupConfig = json match {
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

  def unexpectedJsValueError(x: JsValue) = deserializationError(s"Unexpected JsValue: $x")
}
