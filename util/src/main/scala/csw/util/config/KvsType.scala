package csw.util.config

/**
 * Base trait for objects that can be stored externally in a key/value store
 */
trait KvsType extends Serializable {

  //  /**
  //   * Defines the key under which to store the data
  //   * @return
  //   */
  //  def getKey: String

  /**
   * Holds the typed key/value pairs
   */
  def data: ConfigData

  /**
   * The number of key/value pairs
   */
  def size = data.size

  /**
   * Returns the value for the key, if found
   */
  def get(key: Key): Option[key.Value] = data.get(key)

  /**
   * Returns the set of keys in the data map
   */
  def getKeys: Set[Key] = data.data.keySet
}
