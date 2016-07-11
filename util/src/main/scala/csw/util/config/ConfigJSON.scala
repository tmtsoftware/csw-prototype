package csw.util.config

import csw.util.config.Configurations._
import csw.util.config.Events._
import csw.util.config.StateVariable._
import csw.util.config.UnitsOfMeasure.Units
import spray.json._

/**
 * TMT Source Code: 5/10/16.
 */
object ConfigJSON extends DefaultJsonProtocol {
  implicit val unitsFormat = jsonFormat1(Units.apply)

  // JSON formats
  implicit val charItemFormat = jsonFormat3(CharItem.apply)
  implicit val shortItemFormat = jsonFormat3(ShortItem.apply)
  implicit val intItemFormat = jsonFormat3(IntItem.apply)
  implicit val longItemFormat = jsonFormat3(LongItem.apply)
  implicit val floatItemFormat = jsonFormat3(FloatItem.apply)
  implicit val doubleItemFormat = jsonFormat3(DoubleItem.apply)
  implicit val booleanItemFormat = jsonFormat3(BooleanItem.apply)
  implicit val stringItemFormat = jsonFormat3(StringItem.apply)
  implicit val doubleMatrixItemFormat = jsonFormat3(DoubleMatrixItem.apply)
  implicit val doubleArrayItemFormat = jsonFormat3(DoubleArrayItem.apply)
  implicit val floatMatrixItemFormat = jsonFormat3(FloatMatrixItem.apply)
  implicit val floatArrayItemFormat = jsonFormat3(FloatArrayItem.apply)
  implicit val intMatrixItemFormat = jsonFormat3(IntMatrixItem.apply)
  implicit val intArrayItemFormat = jsonFormat3(IntArrayItem.apply)
  implicit val byteMatrixItemFormat = jsonFormat3(ByteMatrixItem.apply)
  implicit val byteArrayItemFormat = jsonFormat3(ByteArrayItem.apply)
  implicit val shortMatrixItemFormat = jsonFormat3(ShortMatrixItem.apply)
  implicit val shortArrayItemFormat = jsonFormat3(ShortArrayItem.apply)
  implicit val longMatrixItemFormat = jsonFormat3(LongMatrixItem.apply)
  implicit val longArrayItemFormat = jsonFormat3(LongArrayItem.apply)

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

  implicit def itemsFormat: JsonFormat[ConfigData] = new JsonFormat[ConfigData] {
    def write(items: ConfigData) = JsArray(items.map(writeItem(_)).toList: _*)

    def read(json: JsValue) = json match {
      case a: JsArray ⇒ a.elements.map((el: JsValue) ⇒ readItemAndType(el)).toSet
      case _          ⇒ unexpectedJsValueError(json)
    }
  }

  implicit val configKeyFormat = jsonFormat2(ConfigKey.apply)
  implicit val obsIdFormat = jsonFormat1(ObsId.apply)
  implicit val eventTimeFormat = jsonFormat1(EventTime.apply)
  implicit val eventInfoFormat = jsonFormat4(EventInfo.apply)

  // JSON type tags
  private val charType = classOf[CharItem].getSimpleName
  private val shortType = classOf[ShortItem].getSimpleName
  private val integerType = classOf[IntItem].getSimpleName
  private val longType = classOf[LongItem].getSimpleName
  private val floatType = classOf[FloatItem].getSimpleName
  private val doubleType = classOf[DoubleItem].getSimpleName
  private val booleanType = classOf[BooleanItem].getSimpleName
  private val stringType = classOf[StringItem].getSimpleName
  private val doubleMatrixType = classOf[DoubleMatrixItem].getSimpleName
  private val doubleArrayType = classOf[DoubleArrayItem].getSimpleName
  private val floatMatrixType = classOf[FloatMatrixItem].getSimpleName
  private val floatArrayType = classOf[FloatArrayItem].getSimpleName
  private val intMatrixType = classOf[IntMatrixItem].getSimpleName
  private val intArrayType = classOf[IntArrayItem].getSimpleName
  private val byteMatrixType = classOf[ByteMatrixItem].getSimpleName
  private val byteArrayType = classOf[ByteArrayItem].getSimpleName
  private val shortMatrixType = classOf[ShortMatrixItem].getSimpleName
  private val shortArrayType = classOf[ShortArrayItem].getSimpleName
  private val longMatrixType = classOf[LongMatrixItem].getSimpleName
  private val longArrayType = classOf[LongArrayItem].getSimpleName

  // config and event type JSON tags
  private val setupConfigType = classOf[SetupConfig].getSimpleName
  private val observeConfigType = classOf[ObserveConfig].getSimpleName
  private val waitConfigType = classOf[WaitConfig].getSimpleName
  private val statusEventType = classOf[StatusEvent].getSimpleName
  private val observeEventType = classOf[ObserveEvent].getSimpleName
  private val systemEventType = classOf[SystemEvent].getSimpleName
  private val curentStateType = classOf[CurrentState].getSimpleName
  private val demandStateType = classOf[DemandState].getSimpleName

  private def unexpectedJsValueError(x: JsValue) = deserializationError(s"Unexpected JsValue: $x")

  // XXX TODO Use JNumber?
  def writeItem[S, I /*, J */ ](item: Item[S /*, J */ ]): JsValue = {
    val result: (JsString, JsValue) = item match {
      case ci: CharItem         ⇒ (JsString(charType), charItemFormat.write(ci))
      case si: ShortItem        ⇒ (JsString(shortType), shortItemFormat.write(si))
      case ii: IntItem          ⇒ (JsString(integerType), intItemFormat.write(ii))
      case li: LongItem         ⇒ (JsString(longType), longItemFormat.write(li))
      case fi: FloatItem        ⇒ (JsString(floatType), floatItemFormat.write(fi))
      case di: DoubleItem       ⇒ (JsString(doubleType), doubleItemFormat.write(di))
      case bi: BooleanItem      ⇒ (JsString(booleanType), booleanItemFormat.write(bi))
      case si: StringItem       ⇒ (JsString(stringType), stringItemFormat.write(si))
      case di: DoubleMatrixItem ⇒ (JsString(doubleMatrixType), doubleMatrixItemFormat.write(di))
      case di: DoubleArrayItem  ⇒ (JsString(doubleArrayType), doubleArrayItemFormat.write(di))
      case di: FloatMatrixItem  ⇒ (JsString(floatMatrixType), floatMatrixItemFormat.write(di))
      case di: FloatArrayItem   ⇒ (JsString(floatArrayType), floatArrayItemFormat.write(di))
      case di: IntMatrixItem    ⇒ (JsString(intMatrixType), intMatrixItemFormat.write(di))
      case di: IntArrayItem     ⇒ (JsString(intArrayType), intArrayItemFormat.write(di))
      case di: ByteMatrixItem   ⇒ (JsString(byteMatrixType), byteMatrixItemFormat.write(di))
      case di: ByteArrayItem    ⇒ (JsString(byteArrayType), byteArrayItemFormat.write(di))
      case di: ShortMatrixItem  ⇒ (JsString(shortMatrixType), shortMatrixItemFormat.write(di))
      case di: ShortArrayItem   ⇒ (JsString(shortArrayType), shortArrayItemFormat.write(di))
      case di: LongMatrixItem   ⇒ (JsString(longMatrixType), longMatrixItemFormat.write(di))
      case di: LongArrayItem    ⇒ (JsString(longArrayType), longArrayItemFormat.write(di))
      case gi: GenericItem[S]   ⇒ (JsString(gi.typeName), gi.toJson)
    }
    JsObject("itemType" → result._1, "item" → result._2)
  }

  def readItemAndType(json: JsValue): Item[_ /*, _ */ ] = json match {
    case JsObject(fields) ⇒
      (fields("itemType"), fields("item")) match {
        case (JsString(`charType`), item)         ⇒ charItemFormat.read(item)
        case (JsString(`shortType`), item)        ⇒ shortItemFormat.read(item)
        case (JsString(`integerType`), item)      ⇒ intItemFormat.read(item)
        case (JsString(`longType`), item)         ⇒ longItemFormat.read(item)
        case (JsString(`floatType`), item)        ⇒ floatItemFormat.read(item)
        case (JsString(`doubleType`), item)       ⇒ doubleItemFormat.read(item)
        case (JsString(`booleanType`), item)      ⇒ booleanItemFormat.read(item)
        case (JsString(`stringType`), item)       ⇒ stringItemFormat.read(item)
        case (JsString(`doubleMatrixType`), item) ⇒ doubleMatrixItemFormat.read(item)
        case (JsString(`doubleArrayType`), item)  ⇒ doubleArrayItemFormat.read(item)
        case (JsString(`floatMatrixType`), item)  ⇒ floatMatrixItemFormat.read(item)
        case (JsString(`floatArrayType`), item)   ⇒ floatArrayItemFormat.read(item)
        case (JsString(`intMatrixType`), item)    ⇒ intMatrixItemFormat.read(item)
        case (JsString(`intArrayType`), item)     ⇒ intArrayItemFormat.read(item)
        case (JsString(`byteMatrixType`), item)   ⇒ byteMatrixItemFormat.read(item)
        case (JsString(`byteArrayType`), item)    ⇒ byteArrayItemFormat.read(item)
        case (JsString(`shortMatrixType`), item)  ⇒ shortMatrixItemFormat.read(item)
        case (JsString(`shortArrayType`), item)   ⇒ shortArrayItemFormat.read(item)
        case (JsString(`longMatrixType`), item)   ⇒ longMatrixItemFormat.read(item)
        case (JsString(`longArrayType`), item)    ⇒ longArrayItemFormat.read(item)
        case (JsString(typeTag), item) ⇒
          GenericItem.lookup(typeTag) match {
            case Some(jsonReaderFunc) ⇒ jsonReaderFunc(item)
            case None                 ⇒ unexpectedJsValueError(item)
          }
        case _ ⇒ unexpectedJsValueError(json)
      }
    case _ ⇒ unexpectedJsValueError(json)
  }

  /**
   * Writes a config or event to JSON
   *
   * @param config any instance of ConfigType
   * @tparam A the type of the config (implied)
   * @return a JsValue object representing the config
   */
  def writeConfig[A <: ConfigType[_]](config: A): JsValue = {
    JsObject(
      "configType" → JsString(config.typeName),
      "configKey" → configKeyFormat.write(config.configKey),
      "items" → config.items.toJson
    )
  }

  /**
   * Writes an event to JSON
   *
   * @param event any instance of EventType
   * @tparam A the type of the event (implied)
   * @return a JsValue object representing the event
   */
  def writeEvent[A <: EventType[_]](event: A): JsValue = {
    JsObject(
      "eventType" → JsString(event.typeName),
      "eventInfo" → eventInfoFormat.write(event.info),
      "items" → event.items.toJson
    )
  }

  /**
   * Reads a config back from JSON
   *
   * @param json the parsed JSON
   * @tparam A the type of the config (use Any and match on the type if you don't know)
   * @return an instance of the given config type, or an exception if the JSON is not valid for that type
   */
  def readConfig[A <: ConfigType[_]](json: JsValue): A = {
    json match {
      case JsObject(fields) ⇒
        (fields("configType"), fields("configKey"), fields("items")) match {
          case (JsString(typeName), configKey, items) ⇒
            val ck = configKey.convertTo[ConfigKey]
            typeName match {
              case `setupConfigType`   ⇒ SetupConfig(ck, itemsFormat.read(items)).asInstanceOf[A]
              case `observeConfigType` ⇒ ObserveConfig(ck, itemsFormat.read(items)).asInstanceOf[A]
              case `waitConfigType`    ⇒ WaitConfig(ck, itemsFormat.read(items)).asInstanceOf[A]
              case `curentStateType`   ⇒ CurrentState(ck, itemsFormat.read(items)).asInstanceOf[A]
              case `demandStateType`   ⇒ DemandState(ck, itemsFormat.read(items)).asInstanceOf[A]
              case _                   ⇒ unexpectedJsValueError(json)
            }
          case _ ⇒ unexpectedJsValueError(json)
        }
      case _ ⇒ unexpectedJsValueError(json)
    }
  }

  /**
   * Reads an event back from JSON
   *
   * @param json the parsed JSON
   * @tparam A the type of the event (use Any and match on the type if you don't know)
   * @return an instance of the given event type, or an exception if the JSON is not valid for that type
   */
  def readEvent[A <: EventType[_]](json: JsValue): A = {
    json match {
      case JsObject(fields) ⇒
        (fields("eventType"), fields("eventInfo"), fields("items")) match {
          case (JsString(typeName), eventInfo, items) ⇒
            val info = eventInfo.convertTo[EventInfo]
            typeName match {
              case `statusEventType`  ⇒ StatusEvent(info, itemsFormat.read(items)).asInstanceOf[A]
              case `observeEventType` ⇒ ObserveEvent(info, itemsFormat.read(items)).asInstanceOf[A]
              case `systemEventType`  ⇒ SystemEvent(info, itemsFormat.read(items)).asInstanceOf[A]
              case _                  ⇒ unexpectedJsValueError(json)
            }
          case _ ⇒ unexpectedJsValueError(json)
        }
      case _ ⇒ unexpectedJsValueError(json)
    }
  }
}

