package csw.util.config3

import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.Units
import spray.json.{JsArray, JsObject, JsString, JsValue, JsonFormat, JsonReader}

object GenericItem {

  /**
   * type of a function that reads JSON and returns a new GenericItem
   */
  type JsonReaderFunc = JsValue ⇒ GenericItem[_]

  // Used to register a JsonFormat instance to use to read and write JSON for a given GenericItem subclass
  private var jsonReaderMap = Map[String, JsonReaderFunc]()

  /**
   * Sets the JSON reader and writer for a GenericItem
   *
   * @param typeName the tag name in JSON
   * @param jsonReader implements creating this object from JSON
   * @tparam T the (scala) type parameter of the GenericItem
   */
  def register[T](typeName: String, jsonReader: JsonReaderFunc): Unit = jsonReaderMap += (typeName → jsonReader)

  /**
   * Lookup the JsonFormat for the given type name
   *
   * @param typeName the JSON key
   * @return the JsonFormat, if registered
   */
  def lookup(typeName: String): Option[JsonReaderFunc] = jsonReaderMap.get(typeName)
}

/**
 * The type of a value for an GenericKey
 *
 * @param keyName the name of the key
 * @param value   the value for the key
 * @param units   the units of the value
 */
sealed case class GenericItem[S: JsonFormat](keyName: String, value: Vector[S], units: Units) extends Item[S, S] {

  /**
   * @return a JsValue representing this item
   */
  def toJson: JsValue = {
    val valueFormat = implicitly[JsonFormat[S]]
    val unitsFormat = ConfigJSON.unitsFormat
    JsObject(
      "keyName" → JsString(keyName),
      "value" → JsArray(value.map(valueFormat.write)),
      "units" → unitsFormat.write(units)
    )
  }

  /**
   * Java API
   *
   * @return the value at the given index
   */
  override def jget(index: Int): S = value(index)

  /**
   * Set the units of the value
   *
   * @param unitsIn the units to set
   * @return a copy of this item with the given units set
   */
  override def withUnits(unitsIn: Units): Item[S, S] = copy(units = unitsIn)
}

/**
 * A key of S values
 *
 * @param nameIn the name of the key
 */
case class GenericKey[S: JsonFormat](nameIn: String) extends Key[S, S](nameIn) {

  /**
   * Sets the values for the key using a variable number of arguments
   *
   * @param v the values
   * @return a new item containing the key name, values and no units
   */
  override def set(v: S*): Item[S, S] = GenericItem(keyName, v.toVector, UnitsOfMeasure.NoUnits)

  /**
   * Java API: Sets the values for the key using a variable number of arguments
   *
   * @param v the values
   * @return a new item containing the key name, values and no units
   */
  override def jset(v: S*): Item[S, S] = GenericItem(keyName, v.toVector, UnitsOfMeasure.NoUnits)
}

