package csw.util.config

import csw.util.config.Configurations.{ConfigData, ConfigKey, ConfigType}
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}

/**
 * TMT Source Code: 9/28/16.
 */
case class StructItem(keyName: String, values: Vector[Struct], units: Units) extends Item[Struct] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)

}

final case class StructKey(nameIn: String) extends Key[Struct, StructItem](nameIn) {
  override def set(v: Vector[Struct], units: Units = NoUnits) = StructItem(keyName, v, units)

  override def set(v: Struct*) = StructItem(keyName, v.toVector, units = NoUnits)
}

/**
 * A configuration for setting telescope and instrument parameters
 *
 * @param key       identifies the target subsystem
 * @param items     an optional initial set of items (keys with values)
 */
case class Struct(key: String, items: ConfigData = Set.empty[Item[_]]) extends ConfigType[Struct] {

  override val configKey = ConfigKey(Subsystem.TEST, key)

  override def create(data: ConfigData) = Struct(key, data)

  def dataToString1 = items.mkString("(", ", ", ")")

  override def toString = s"${configKey.prefix}$dataToString1"
}