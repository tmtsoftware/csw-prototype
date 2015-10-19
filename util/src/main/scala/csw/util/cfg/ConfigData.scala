package csw.util.cfg

/**
 * The shared class for storing telemetry and configuration data.
 */
case class Key(name: String) extends Serializable {
  type Value

  override def toString = name
}

object Key {
  type Aux[A] = Key { type Value = A }

  def create[A](name: String): Key.Aux[A] = new Key(name) { type Value = A }
}

case class ConfigData(data: Map[Key, Any] = Map.empty) extends Serializable {

  def get[A](key: Key): Option[key.Value] = data.get(key).asInstanceOf[Option[key.Value]]

  def set[A](key: Key.Aux[A], value: A): ConfigData = ConfigData(data + (key -> value))

  def remove(key: Key): ConfigData = ConfigData(data - key)

  def size = data.size

  override def toString = data.mkString("(", ", ", ")")
}

