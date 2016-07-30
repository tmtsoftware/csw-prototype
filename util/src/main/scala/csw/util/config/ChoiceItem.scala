package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json._
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.Vector
import scala.language.implicitConversions

case class Choice(name: String) {
  override def toString = name
}

object Choice {
  // Used for serializing choices to JSON
  // implicit def format = spray.json.DefaultJsonProtocol.jsonFormat1(Choice.apply)

  implicit def toChoice(name: String) = new Choice(name)
}

case class Choices(values: Set[Choice]) {
  def contains(one: Choice) = values.contains(one)

  override def toString = values.mkString("(", ",", ")")
}

object Choices {
  def apply(values: String*) = new Choices(values.map(Choice(_)).toSet)
}

/*
object ChoicesProtocol extends DefaultJsonProtocol {
  implicit object ChoicesJsonFormat extends JsonFormat[Choices] {
    def write(c: Choices) = c.values.toJson

    def read(value: JsValue) = value match {
      case JsArray(elements) => Choices(elements.map(_.convertTo[Choice]).toSet)
      case _ => deserializationError("Choices expected")
    }
  }
}
*/

final case class ChoiceItem(keyName: String, possibles: Choices, values: Vector[Choice], units: Units) extends Item[Choice] {
  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
  * A key for an enum value
  *
  * @param nameIn  the name of the key
  * @param choices the available choices, the values set must be in the choices
  */
final case class ChoiceKey(nameIn: String, choices: Choices) extends Key[Choice, ChoiceItem](nameIn) {
  override def set(v: Vector[Choice], units: Units = NoUnits) = {
    // Check to make sure set values are in the choices -- could be done with type system
    assert(v.forall(choices.contains(_)), s"Bad choice for key: $nameIn which must be one of: $choices")
    ChoiceItem(keyName, choices, v, units)
  }

  override def set(v: Choice*) = {
    // Check to make sure set values are in the choices -- could be done with type system
    assert(v.forall(choices.contains(_)), s"Bad choice for key: $nameIn which must be one of: $choices")
    ChoiceItem(keyName, choices, v.toVector, units = NoUnits)
  }
}
