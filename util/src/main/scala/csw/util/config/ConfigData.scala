package csw.util.config

/**
 * A key that defines the type of value it expects.
 * @param name the name of the key
 */
abstract class Key(val name: String) extends Serializable {
  type Value

  override def toString = name
}

/**
 * The shared class for storing telemetry and configuration data.
 *
 * @param data a map of typed keys and values
 */
case class  ConfigData(data: Map[Key, Any] = Map.empty[Key, Any]) extends Serializable {
  // Should we just use a mutable Map?
  def get(key: Key): Option[key.Value] = data.get(key).asInstanceOf[Option[key.Value]]

  def set(key: Key)(value: key.Value): ConfigData = ConfigData(data + (key -> value))

  def remove(key: Key): ConfigData = ConfigData(data - key)

  def size = data.size

  override def toString = data.mkString("(", ", ", ")")
}

