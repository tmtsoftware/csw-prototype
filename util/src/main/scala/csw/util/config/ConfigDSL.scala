package csw.util.config

import csw.util.config.Configurations.ConfigType
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}

/**
  * TMT Source Code: 7/9/16.
  */
object ConfigDSL {

  /**
    * Add an item to the configuration T
    *
    * @param sc the configuration to contain the items
    * @param item the item to add
    * @return a new configuration with the item added or updating previously existing item
    */
  def add[I<:Item[_], T<:ConfigType[T]](sc: T, item: I):T = sc.add(item)

  /**
    * Add one or more items to the configuration
    *
    * @param sc the configuration to contain the items
    * @param items the items to add
    * @return a new configuration with the items added or updated previously existing item
    */
  def madd[I<:Item[_], T<:ConfigType[T]](sc: T, items: I*):T = sc.madd(items:_*)

  /**
    * Remove an item from the configuration based on key
    *
    * @param sc the configuration that contains items
    * @param key the key of the item to remove
    * @return a new configuration with the item with key removed or unchanged if not present
    */
  def remove[S, I<:Item[S], T<:ConfigType[T]](sc: T, key: Key[S,I]):T = sc.remove(key)

  /**
    * Remove an item from the configuration based on the item contents
    *
    * @param sc the configuration that contains items
    * @param item the item to be removed
    * @return a new configuration with the item removed or unchanged if not present
    */
  def remove[I<:Item[_], T<:ConfigType[T]](sc: T, item: I):T = sc.remove(item)

  /**
    * Find the item in the configuration
    *
    * @param sc the configuration that contains items
    * @param key the key of the item that is needed
    * @return returns the item itself or the NoSuchElementException if the key is not present
    */
  def item[S, I<:Item[S], T<:ConfigType[T]](sc: T, key: Key[S,I]):I = sc.item(key)

  /**
    * Find the item in the configuraiton and return as Option with the item
    *
    * @param sc the configuration that contains items
    * @param key the key of the item that is needed
    * @return the item as an Option or None if the item is not found
    */
  def get[S, I<:Item[S], T<:ConfigType[T]](sc: T, key:Key[S,I]):Option[I] = sc.get(key)

  /**
    * Finds an item and returns the value at an index as an Option
    * This is a shortcut for get item and get(index) value
    *
    * @param sc the configuration that contains items
    * @param key the key of the item that is needed
    * @param index the index of the value needed
    * @return the index value as an Option or None if the item with key is not present or there is no value at the index
    */
  def get[S, I<:Item[S], T<:ConfigType[T]](sc: T, key: Key[S,I], index:Int):Option[S] = sc.get(key).flatMap((i:Item[S]) => i.get(index))

  /**
    * Convenience function to return the first value item
    *
    * @param item the item that contains values
    * @return The item at the front of the values
    */
  def head[S, I<:Item[S]](item: I):S = item.head

  /**
    * Returns the value for an item at the index
    *
    * @param item the item that contains values
    * @param index the index of the needed value
    * @return the item's index value or throws an IndexOutOfBoundsException
    */
  def value[S, I<:Item[S]](item: I, index: Int):S = item.value(index)

  /**
    * Returns the value for an item at the index as an Option
    *
    * @param item the item that contains values
    * @param index the index of the needed value
    * @return the item's index value as an Option (i.e. Some(value)) or None if the index is inappropriate
    */
  def get[S, I<:Item[S]](item: I, index: Int):Option[S] = item.get(index)

  /**
    * Returns the vector of values for the item
    *
    * @param item the item with the needed values
    * @return all of the values for the item as a Vector
    */
  def values[S, I<:Item[S]](item: I):Vector[S] = item.values

  /**
    * Create an item by setting a key with a Vector of values associated with the key
    *
    * @param key the key that is used to create the needed item
    * @param v a Vector of values of the item's type that is being used to set the item
    * @param units optional units for the item
    * @return a new item of the type associated with the key
    */
  def set[S, I<:Item[S]](key:Key[S,I], v: Vector[S], units:Units = NoUnits):I = key.set(v, units)

  /**
    * Create an item by settign a key with one or more values associated with the key
    * @param key the key that isused to crate the needed item
    * @param v a varargs argument with one or more values of the item's type
    * @return a new item of the type associated with the key
    */
  def set[S, I<:Item[S]](key:Key[S,I], v: S*):I = key.set(v:_*)

}
