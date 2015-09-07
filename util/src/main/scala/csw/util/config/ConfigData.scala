package csw.util.config

/**
 * The shared class for storing telemetry and configuration data.
 */
abstract class Key(name: String) extends Serializable {
  type Value

  override def toString = name
}

class ConfigData(val data: Map[Key, Any]) extends Serializable {
  // Should we just use a mutable Map?
  def get(key: Key): Option[key.Value] = data.get(key).asInstanceOf[Option[key.Value]]

  def set(key: Key)(value: key.Value): ConfigData = ConfigData(data + (key -> value))

  def remove(key: Key): ConfigData = ConfigData(data - key)

  def size = data.size

  override def toString = data.mkString("(", ", ", ")")
}

object ConfigData {
  def apply(data: Map[Key, Any] = Map.empty[Key, Any]): ConfigData = new ConfigData(data)
}
