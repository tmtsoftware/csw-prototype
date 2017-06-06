package csw.util.itemSet

import java.time.Instant

import csw.util.itemSet.ItemSets._
import csw.util.itemSet.Events._
import csw.util.itemSet.StateVariable._
import csw.util.itemSet.UnitsOfMeasure.Units
import spray.json._

/**
 * TMT Source Code: 5/10/16.
 */
//noinspection TypeAnnotation
object ItemSetJson extends DefaultJsonProtocol {
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
  implicit val choiceFormat = jsonFormat1(Choice.apply)
  implicit val choicesFormat = jsonFormat1(Choices.apply)
  implicit val choiceItemFormat = jsonFormat4(ChoiceItem.apply)

  implicit def structFormat: JsonFormat[Struct] = new JsonFormat[Struct] {
    def write(s: Struct): JsValue = JsObject(
      "itemSetType" -> JsString(s.typeName),
      "name" -> JsString(s.name),
      "items" -> s.items.toJson
    )

    def read(json: JsValue): Struct = {
      json match {
        case JsObject(fields) =>
          (fields("itemSetType"), fields("name"), fields("items")) match {
            case (JsString(typeName), JsString(name), items) =>
              typeName match {
                case `structType` => Struct(name, itemsFormat.read(items))
                case _            => unexpectedJsValueError(json)
              }
            case _ => unexpectedJsValueError(json)
          }
        case _ => unexpectedJsValueError(json)
      }
    }
  }

  implicit val structItemFormat = jsonFormat3(StructItem.apply)

  implicit def subsystemFormat: JsonFormat[Subsystem] = new JsonFormat[Subsystem] {
    def write(obj: Subsystem) = JsString(obj.name)

    def read(value: JsValue): Subsystem = {
      value match {
        case JsString(subsystemStr) => Subsystem.lookup(subsystemStr) match {
          case Some(subsystem) => subsystem
          case None            => Subsystem.BAD
        }
        // With malformed JSON, return BAD
        case _ => Subsystem.BAD
      }
    }
  }

  implicit def itemsFormat: JsonFormat[ItemsData] = new JsonFormat[ItemsData] {
    def write(items: ItemsData) = JsArray(items.map(writeItem(_)).toList: _*)

    def read(json: JsValue) = json match {
      case a: JsArray => a.elements.map((el: JsValue) => readItemAndType(el)).toSet
      case _          => unexpectedJsValueError(json)
    }
  }

  implicit def eventTimeFormat: JsonFormat[EventTime] = new JsonFormat[EventTime] {
    def write(et: EventTime): JsValue = JsString(et.toString)

    def read(json: JsValue): EventTime = json match {
      case JsString(s) => Instant.parse(s)
      case _           => unexpectedJsValueError(json)
    }
  }

  implicit val itemSetKeyFormat = jsonFormat2(ItemSetKey.apply)
  implicit val obsIdFormat = jsonFormat1(ObsId.apply)
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
  private val choiceType = classOf[ChoiceItem].getSimpleName
  private val structItemType = classOf[StructItem].getSimpleName

  // config and event type JSON tags
  private val setupType = classOf[Setup].getSimpleName
  private val observeType = classOf[Observe].getSimpleName
  private val waitType = classOf[Wait].getSimpleName
  private val statusEventType = classOf[StatusEvent].getSimpleName
  private val observeEventType = classOf[ObserveEvent].getSimpleName
  private val systemEventType = classOf[SystemEvent].getSimpleName
  private val curentStateType = classOf[CurrentState].getSimpleName
  private val demandStateType = classOf[DemandState].getSimpleName
  private val structType = classOf[Struct].getSimpleName

  private def unexpectedJsValueError(x: JsValue) = deserializationError(s"Unexpected JsValue: $x")

  // XXX TODO Use JNumber?
  def writeItem[S, I /*, J */ ](item: Item[S /*, J */ ]): JsValue = {
    val result: (JsString, JsValue) = item match {
      case i: CharItem         => (JsString(charType), charItemFormat.write(i))
      case i: ShortItem        => (JsString(shortType), shortItemFormat.write(i))
      case i: IntItem          => (JsString(integerType), intItemFormat.write(i))
      case i: LongItem         => (JsString(longType), longItemFormat.write(i))
      case i: FloatItem        => (JsString(floatType), floatItemFormat.write(i))
      case i: DoubleItem       => (JsString(doubleType), doubleItemFormat.write(i))
      case i: BooleanItem      => (JsString(booleanType), booleanItemFormat.write(i))
      case i: StringItem       => (JsString(stringType), stringItemFormat.write(i))
      case i: DoubleMatrixItem => (JsString(doubleMatrixType), doubleMatrixItemFormat.write(i))
      case i: DoubleArrayItem  => (JsString(doubleArrayType), doubleArrayItemFormat.write(i))
      case i: FloatMatrixItem  => (JsString(floatMatrixType), floatMatrixItemFormat.write(i))
      case i: FloatArrayItem   => (JsString(floatArrayType), floatArrayItemFormat.write(i))
      case i: IntMatrixItem    => (JsString(intMatrixType), intMatrixItemFormat.write(i))
      case i: IntArrayItem     => (JsString(intArrayType), intArrayItemFormat.write(i))
      case i: ByteMatrixItem   => (JsString(byteMatrixType), byteMatrixItemFormat.write(i))
      case i: ByteArrayItem    => (JsString(byteArrayType), byteArrayItemFormat.write(i))
      case i: ShortMatrixItem  => (JsString(shortMatrixType), shortMatrixItemFormat.write(i))
      case i: ShortArrayItem   => (JsString(shortArrayType), shortArrayItemFormat.write(i))
      case i: LongMatrixItem   => (JsString(longMatrixType), longMatrixItemFormat.write(i))
      case i: LongArrayItem    => (JsString(longArrayType), longArrayItemFormat.write(i))
      case i: ChoiceItem       => (JsString(choiceType), choiceItemFormat.write(i))
      case i: StructItem       => (JsString(structItemType), structItemFormat.write(i))
      case i: GenericItem[S]   => (JsString(i.typeName), i.toJson)
    }
    JsObject("itemType" -> result._1, "item" -> result._2)
  }

  def readItemAndType(json: JsValue): Item[_ /*, _ */ ] = json match {
    case JsObject(fields) =>
      (fields("itemType"), fields("item")) match {
        case (JsString(`charType`), item)         => charItemFormat.read(item)
        case (JsString(`shortType`), item)        => shortItemFormat.read(item)
        case (JsString(`integerType`), item)      => intItemFormat.read(item)
        case (JsString(`longType`), item)         => longItemFormat.read(item)
        case (JsString(`floatType`), item)        => floatItemFormat.read(item)
        case (JsString(`doubleType`), item)       => doubleItemFormat.read(item)
        case (JsString(`booleanType`), item)      => booleanItemFormat.read(item)
        case (JsString(`stringType`), item)       => stringItemFormat.read(item)
        case (JsString(`doubleMatrixType`), item) => doubleMatrixItemFormat.read(item)
        case (JsString(`doubleArrayType`), item)  => doubleArrayItemFormat.read(item)
        case (JsString(`floatMatrixType`), item)  => floatMatrixItemFormat.read(item)
        case (JsString(`floatArrayType`), item)   => floatArrayItemFormat.read(item)
        case (JsString(`intMatrixType`), item)    => intMatrixItemFormat.read(item)
        case (JsString(`intArrayType`), item)     => intArrayItemFormat.read(item)
        case (JsString(`byteMatrixType`), item)   => byteMatrixItemFormat.read(item)
        case (JsString(`byteArrayType`), item)    => byteArrayItemFormat.read(item)
        case (JsString(`shortMatrixType`), item)  => shortMatrixItemFormat.read(item)
        case (JsString(`shortArrayType`), item)   => shortArrayItemFormat.read(item)
        case (JsString(`longMatrixType`), item)   => longMatrixItemFormat.read(item)
        case (JsString(`longArrayType`), item)    => longArrayItemFormat.read(item)
        case (JsString(`choiceType`), item)       => choiceItemFormat.read(item)
        case (JsString(`structItemType`), item)   => structItemFormat.read(item)
        case (JsString(typeTag), item) =>
          GenericItem.lookup(typeTag) match {
            case Some(jsonReaderFunc) => jsonReaderFunc(item)
            case None                 => unexpectedJsValueError(item)
          }
        case _ => unexpectedJsValueError(json)
      }
    case _ => unexpectedJsValueError(json)
  }

  /**
   * Handles conversion of ItemSetInfo to/from JSON
   */
  implicit def itemSetInfoFormat: RootJsonFormat[ItemSetInfo] = new RootJsonFormat[ItemSetInfo] {
    override def read(json: JsValue): ItemSetInfo = json.asJsObject.getFields("obsId", "runId") match {
      case Seq(JsString(obsId), JsString(runId)) =>
        ItemSetInfo(ObsId(obsId), RunId(runId))
    }

    override def write(obj: ItemSetInfo): JsValue = JsObject(
      "obsId" -> JsString(obj.obsId.obsId),
      "runId" -> JsString(obj.runId.id)
    )
  }

  /**
   * Writes a SequenceItemSet to JSON
   *
   * @param itemSet any instance of SequenceItemSet
   * @tparam A the type of the item set (implied)
   * @return a JsValue object representing the SequenceItemSet
   */
  def writeSequenceItemSet[A <: SequenceItemSet](itemSet: A): JsValue = {
    JsObject(
      "itemSetType" -> JsString(itemSet.typeName),
      "itemSetInfo" -> itemSetInfoFormat.write(itemSet.info),
      "itemSetKey" -> itemSetKeyFormat.write(itemSet.itemSetKey),
      "items" -> itemSet.items.toJson
    )
  }

  /**
   * Reads a SequenceItemSet back from JSON
   *
   * @param json the parsed JSON
   * @return an instance of the given SequenceItemSet type, or an exception if the JSON is not valid for that type
   */
  def readSequenceItemSet[A <: SequenceItemSet](json: JsValue): A = {
    json match {
      case JsObject(fields) =>
        (fields("itemSetType"), fields("itemSetInfo"), fields("itemSetKey"), fields("items")) match {
          case (JsString(typeName), itemSetInfo, itemSetKey, items) =>
            val info = itemSetInfo.convertTo[ItemSetInfo]
            val ck = itemSetKey.convertTo[ItemSetKey]
            typeName match {
              case `setupType`   => Setup(info, ck, itemsFormat.read(items)).asInstanceOf[A]
              case `observeType` => Observe(info, ck, itemsFormat.read(items)).asInstanceOf[A]
              case `waitType`    => Wait(info, ck, itemsFormat.read(items)).asInstanceOf[A]
              case _             => unexpectedJsValueError(json)
            }
          case _ => unexpectedJsValueError(json)
        }
      case _ => unexpectedJsValueError(json)
    }
  }

  /**
   * Writes a state variable to JSON
   *
   * @param itemSet any instance of StateVariable
   * @tparam A the type of the StateVariable (implied)
   * @return a JsValue object representing the StateVariable
   */
  def writeStateVariable[A <: StateVariable](itemSet: A): JsValue = {
    JsObject(
      "itemSetType" -> JsString(itemSet.typeName),
      "itemSetKey" -> itemSetKeyFormat.write(itemSet.itemSetKey),
      "items" -> itemSet.items.toJson
    )
  }

  /**
   * Reads a StateVariable back from JSON
   *
   * @param json the parsed JSON
   * @return an instance of the given StateVariable, or an exception if the JSON is not valid for that type
   */
  def readStateVariable[A <: StateVariable](json: JsValue): A = {
    json match {
      case JsObject(fields) =>
        (fields("itemSetType"), fields("itemSetKey"), fields("items")) match {
          case (JsString(typeName), itemSetKey, items) =>
            val ck = itemSetKey.convertTo[ItemSetKey]
            typeName match {
              case `curentStateType` => CurrentState(ck, itemsFormat.read(items)).asInstanceOf[A]
              case `demandStateType` => DemandState(ck, itemsFormat.read(items)).asInstanceOf[A]
              case _                 => unexpectedJsValueError(json)
            }
          case _ => unexpectedJsValueError(json)
        }
      case _ => unexpectedJsValueError(json)
    }
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
      "eventType" -> JsString(event.typeName),
      "eventInfo" -> eventInfoFormat.write(event.info),
      "items" -> event.items.toJson
    )
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
      case JsObject(fields) =>
        (fields("eventType"), fields("eventInfo"), fields("items")) match {
          case (JsString(typeName), eventInfo, items) =>
            val info = eventInfo.convertTo[EventInfo]
            typeName match {
              case `statusEventType`  => StatusEvent(info, itemsFormat.read(items)).asInstanceOf[A]
              case `observeEventType` => ObserveEvent(info, itemsFormat.read(items)).asInstanceOf[A]
              case `systemEventType`  => SystemEvent(info, itemsFormat.read(items)).asInstanceOf[A]
              case _                  => unexpectedJsValueError(json)
            }
          case _ => unexpectedJsValueError(json)
        }
      case _ => unexpectedJsValueError(json)
    }
  }
}

