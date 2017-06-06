package csw.util.param

import java.time.Instant

import csw.util.param.Parameters._
import csw.util.param.Events._
import csw.util.param.StateVariable._
import csw.util.param.UnitsOfMeasure.Units
import spray.json._

/**
 * TMT Source Code: 5/10/16.
 */
//noinspection TypeAnnotation
object ItemSetJson extends DefaultJsonProtocol {
  implicit val unitsFormat = jsonFormat1(Units.apply)

  // JSON formats
  implicit val charItemFormat = jsonFormat3(CharParameter.apply)
  implicit val shortItemFormat = jsonFormat3(ShortParameter.apply)
  implicit val intItemFormat = jsonFormat3(IntParameter.apply)
  implicit val longItemFormat = jsonFormat3(LongParameter.apply)
  implicit val floatItemFormat = jsonFormat3(FloatParameter.apply)
  implicit val doubleItemFormat = jsonFormat3(DoubleParameter.apply)
  implicit val booleanItemFormat = jsonFormat3(BooleanParameter.apply)
  implicit val stringItemFormat = jsonFormat3(StringParameter.apply)
  implicit val doubleMatrixItemFormat = jsonFormat3(DoubleMatrixParameter.apply)
  implicit val doubleArrayItemFormat = jsonFormat3(DoubleArrayParameter.apply)
  implicit val floatMatrixItemFormat = jsonFormat3(FloatMatrixParameter.apply)
  implicit val floatArrayItemFormat = jsonFormat3(FloatArrayParameter.apply)
  implicit val intMatrixItemFormat = jsonFormat3(IntMatrixParameter.apply)
  implicit val intArrayItemFormat = jsonFormat3(IntArrayParameter.apply)
  implicit val byteMatrixItemFormat = jsonFormat3(ByteMatrixParameter.apply)
  implicit val byteArrayItemFormat = jsonFormat3(ByteArrayParameter.apply)
  implicit val shortMatrixItemFormat = jsonFormat3(ShortMatrixParameter.apply)
  implicit val shortArrayItemFormat = jsonFormat3(ShortArrayParameter.apply)
  implicit val longMatrixItemFormat = jsonFormat3(LongMatrixParameter.apply)
  implicit val longArrayItemFormat = jsonFormat3(LongArrayParameter.apply)
  implicit val choiceFormat = jsonFormat1(Choice.apply)
  implicit val choicesFormat = jsonFormat1(Choices.apply)
  implicit val choiceItemFormat = jsonFormat4(ChoiceParameter.apply)

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

  implicit val structItemFormat = jsonFormat3(StructParameter.apply)

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

  implicit def itemsFormat: JsonFormat[ParameterSet] = new JsonFormat[ParameterSet] {
    def write(items: ParameterSet) = JsArray(items.map(writeItem(_)).toList: _*)

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

  implicit val itemSetKeyFormat = jsonFormat2(Prefix.apply)
  implicit val obsIdFormat = jsonFormat1(ObsId.apply)
  implicit val eventInfoFormat = jsonFormat4(EventInfo.apply)

  // JSON type tags
  private val charType = classOf[CharParameter].getSimpleName
  private val shortType = classOf[ShortParameter].getSimpleName
  private val integerType = classOf[IntParameter].getSimpleName
  private val longType = classOf[LongParameter].getSimpleName
  private val floatType = classOf[FloatParameter].getSimpleName
  private val doubleType = classOf[DoubleParameter].getSimpleName
  private val booleanType = classOf[BooleanParameter].getSimpleName
  private val stringType = classOf[StringParameter].getSimpleName
  private val doubleMatrixType = classOf[DoubleMatrixParameter].getSimpleName
  private val doubleArrayType = classOf[DoubleArrayParameter].getSimpleName
  private val floatMatrixType = classOf[FloatMatrixParameter].getSimpleName
  private val floatArrayType = classOf[FloatArrayParameter].getSimpleName
  private val intMatrixType = classOf[IntMatrixParameter].getSimpleName
  private val intArrayType = classOf[IntArrayParameter].getSimpleName
  private val byteMatrixType = classOf[ByteMatrixParameter].getSimpleName
  private val byteArrayType = classOf[ByteArrayParameter].getSimpleName
  private val shortMatrixType = classOf[ShortMatrixParameter].getSimpleName
  private val shortArrayType = classOf[ShortArrayParameter].getSimpleName
  private val longMatrixType = classOf[LongMatrixParameter].getSimpleName
  private val longArrayType = classOf[LongArrayParameter].getSimpleName
  private val choiceType = classOf[ChoiceParameter].getSimpleName
  private val structItemType = classOf[StructParameter].getSimpleName

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
  def writeItem[S, I /*, J */ ](item: Parameter[S /*, J */ ]): JsValue = {
    val result: (JsString, JsValue) = item match {
      case i: CharParameter         => (JsString(charType), charItemFormat.write(i))
      case i: ShortParameter        => (JsString(shortType), shortItemFormat.write(i))
      case i: IntParameter          => (JsString(integerType), intItemFormat.write(i))
      case i: LongParameter         => (JsString(longType), longItemFormat.write(i))
      case i: FloatParameter        => (JsString(floatType), floatItemFormat.write(i))
      case i: DoubleParameter       => (JsString(doubleType), doubleItemFormat.write(i))
      case i: BooleanParameter      => (JsString(booleanType), booleanItemFormat.write(i))
      case i: StringParameter       => (JsString(stringType), stringItemFormat.write(i))
      case i: DoubleMatrixParameter => (JsString(doubleMatrixType), doubleMatrixItemFormat.write(i))
      case i: DoubleArrayParameter  => (JsString(doubleArrayType), doubleArrayItemFormat.write(i))
      case i: FloatMatrixParameter  => (JsString(floatMatrixType), floatMatrixItemFormat.write(i))
      case i: FloatArrayParameter   => (JsString(floatArrayType), floatArrayItemFormat.write(i))
      case i: IntMatrixParameter    => (JsString(intMatrixType), intMatrixItemFormat.write(i))
      case i: IntArrayParameter     => (JsString(intArrayType), intArrayItemFormat.write(i))
      case i: ByteMatrixParameter   => (JsString(byteMatrixType), byteMatrixItemFormat.write(i))
      case i: ByteArrayParameter    => (JsString(byteArrayType), byteArrayItemFormat.write(i))
      case i: ShortMatrixParameter  => (JsString(shortMatrixType), shortMatrixItemFormat.write(i))
      case i: ShortArrayParameter   => (JsString(shortArrayType), shortArrayItemFormat.write(i))
      case i: LongMatrixParameter   => (JsString(longMatrixType), longMatrixItemFormat.write(i))
      case i: LongArrayParameter    => (JsString(longArrayType), longArrayItemFormat.write(i))
      case i: ChoiceParameter       => (JsString(choiceType), choiceItemFormat.write(i))
      case i: StructParameter       => (JsString(structItemType), structItemFormat.write(i))
      case i: GenericParameter[S]   => (JsString(i.typeName), i.toJson)
    }
    JsObject("itemType" -> result._1, "item" -> result._2)
  }

  def readItemAndType(json: JsValue): Parameter[_ /*, _ */ ] = json match {
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
          GenericParameter.lookup(typeTag) match {
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
  implicit def itemSetInfoFormat: RootJsonFormat[CommandInfo] = new RootJsonFormat[CommandInfo] {
    override def read(json: JsValue): CommandInfo = json.asJsObject.getFields("obsId", "runId") match {
      case Seq(JsString(obsId), JsString(runId)) =>
        CommandInfo(ObsId(obsId), RunId(runId))
    }

    override def write(obj: CommandInfo): JsValue = JsObject(
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
  def writeSequenceItemSet[A <: SequenceCommand](itemSet: A): JsValue = {
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
  def readSequenceItemSet[A <: SequenceCommand](json: JsValue): A = {
    json match {
      case JsObject(fields) =>
        (fields("itemSetType"), fields("itemSetInfo"), fields("itemSetKey"), fields("items")) match {
          case (JsString(typeName), itemSetInfo, itemSetKey, items) =>
            val info = itemSetInfo.convertTo[CommandInfo]
            val ck = itemSetKey.convertTo[Prefix]
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
            val ck = itemSetKey.convertTo[Prefix]
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

