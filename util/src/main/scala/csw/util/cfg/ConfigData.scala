package csw.util.cfg

/**
 * The shared class for storing telemetry and configuration data.
 * @param name the name of the key
 */
case class Key(name: String) extends Serializable {
  /**
   * The type of the key's value
   */
  type Value

  override def toString = name
}

object Key {
  type Aux[A] = Key { type Value = A }

  /**
   * Creates a key with the given value type and name
   */
  def create[A](name: String): Key.Aux[A] = new Key(name) {
    type Value = A
  }
}

/**
 * Contains a map of typed keys and values
 */
case class ConfigData(data: Map[Key, Any] = Map.empty) extends Serializable {

  /**
   * Gets the value for the given key
   */
  def get[A](key: Key): Option[key.Value] = data.get(key).asInstanceOf[Option[key.Value]]

  /**
   * Immutably sets the value for the given key and returns a new instance
   */
  def set[A](key: Key.Aux[A], value: A): ConfigData = ConfigData(data + (key → value))

  // XXX java API
  final def jset[A](key: Key, value: Object): ConfigData = ConfigData(data + (key → value))

  /**
   * Immutably removes the key and its value and returns a new instance
   */
  def remove(key: Key): ConfigData = ConfigData(data - key)

  /**
   * Returns the number of keys in map
   */
  def size = data.size

  /**
   * Returns true if the data contains the given key
   */
  def contains(key: Key): Boolean = data.contains(key)

  /**
   * Returns a set containing any of the given keys that are missing in the data
   * @param key one or more keys
   */
  def missingKeys(key: Key*): Set[Key] = data.keySet.diff(key.toSet)

  override def toString = data.mkString("(", ", ", ")")
}

