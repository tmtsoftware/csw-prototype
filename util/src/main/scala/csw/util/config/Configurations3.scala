package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.language.implicitConversions

/**
  * Created by gillies on 5/25/17.
  */
object Configurations3 {

  /**
    * A top level key for an ItemSet: combines subsystem and the subsystem's prefix
    *
    * @param subsystem the subsystem that is the target of the itemset
    * @param prefix    the subsystem's prefix
    */
  case class ItemSetKey(subsystem: Subsystem, prefix: String) {
    override def toString = s"[$subsystem, $prefix]"
  }

  /**
    * A top level key for an ItemSet: combines subsystem and the subsystem's prefix
    */
  object ItemSetKey {
    private val SEPARATOR = '.'

    /**
      * Creates a ItemSetKey from the given string
      *
      * @return an ItemSetKey object parsed for the subsystem and prefix
      */
    implicit def stringToItemSetKey(prefix: String): ItemSetKey = ItemSetKey(subsystem(prefix), prefix)

    private def subsystem(keyText: String): Subsystem = {
      assert(keyText != null)
      Subsystem.lookup(keyText.splitAt(keyText.indexOf(SEPARATOR))._1).getOrElse(Subsystem.BAD)
    }
  }

  type ItemsData = Set[Item[_]]

  /**
    * A trait to be mixed in that provides an ItemSet, Key item, subsystem and prefix
    */
  trait ItemSetKeyData {
    self: ItemSet[_] =>
    /**
      * Returns an object providing the subsystem and prefix for the ItemSet
      */
    def itemsetKey: ItemSetKey

    /**
      * The subsystem for the ItemSet
      */
    final def subsystem: Subsystem = itemsetKey.subsystem

    /**
      * The prefix for the ItemSet
      */
    final def prefix: String = itemsetKey.prefix

    override def toString = s"$typeName[$subsystem, $prefix]$dataToString"
  }

  /**
    * The base trait for various ItemSet types (command itemsets or events)
    *
    * @tparam T the subclass of ItemSet
    */
  trait ItemSet[T <: ItemSet[T]] {
    self: T =>

    /**
      * A name identifying the type of itemset, such as "setup", "observe", or "wait".
      * This is used in the JSON and toString output.
      */
    def typeName: String = getClass.getSimpleName

    /**
      * Holds the items for this ItemSet
      */
    def items: ItemsData

    /**
      * The number of items in this ItemSet
      *
      * @return the number of items in the itemset
      */
    def size:Int = items.size

    /**
      * Adds an item to the itemset
      *
      * @param item the item to add
      * @tparam I the Item type
      * @return a new instance of this itemset with the given item added
      */
    def add[I <: Item[_]](item: I): T = doAdd(this, item)

    private def doAdd[I <: Item[_]](c: T, item: I): T = {
      val itemsetRemoved: T = removeByKeyname(c, item.keyName)
      create(itemsetRemoved.items + item)
    }

    /**
      * Adds several items to the itemset
      *
      * @param itemsToAdd the list of items to add to the itemset
      * @tparam I must be a subclass of Item
      * @return a new instance of this itemset with the given item added
      */
    def madd[I <: Item[_]](itemsToAdd: I*): T = itemsToAdd.foldLeft(this)((c, item) => doAdd(c, item))

    /**
      * Returns an Option with the item for the key if found, otherwise None
      *
      * @param key the Key to be used for lookup
      * @return the item for the key, if found
      * @tparam S the Scala value type
      * @tparam I the item type for the Scala value S
      */
    def get[S, I <: Item[S]](key: Key[S, I]): Option[I] = getByKeyname[I](items, key.keyName)

    /**
      * Returns an Option with the item for the key if found, otherwise None. Access with keyname rather
      * than Key
      * @param keyName the keyname to be used for the lookup
      * @tparam I the value type
      */
    def getByName[I <: Item[_]](keyName: String): Option[I] = getByKeyname[I](items, keyName)

    def find[I <: Item[_]](item: I): Option[I] = getByKeyname[I](items, item.keyName)

    /**
      * Return the item associated with a Key rather than an Option
      *
      * @param key the Key to be used for lookup
      * @tparam S the Scala value type
      * @tparam I the Item type associated with S
      * @return the item associated with the Key or a NoSuchElementException if the key does not exist
      */
    final def apply[S, I <: Item[S]](key: Key[S, I]): I = get(key).get

    /**
      * Returns the actual item associated with a key
      *
      * @param key the Key to be used for lookup
      * @tparam S the Scala value type
      * @tparam I the Item type associated with S
      * @return the item associated with the key or a NoSuchElementException if the key does not exist
      */
    final def item[S, I <: Item[S]](key: Key[S, I]): I = get(key).get

    /**
      * Returns true if the key exists in the itemset
      *
      * @param key the key to check for
      * @return true if the key is found
      * @tparam S the Scala value type
      * @tparam I the type of the Item associated with the key
      */
    def exists[S, I <: Item[S]](key: Key[S, I]): Boolean = get(key).isDefined

    /**
      * Remove an item from the itemset by key
      *
      * @param key the Key to be used for removal
      * @tparam S the Scala value type
      * @tparam I the item type used with Scala type S
      * @return a new T, where T is an ItemSet child with the key removed or identical if the key is not present
      */
    def remove[S, I <: Item[S]](key: Key[S, I]): T = removeByKeyname(this, key.keyName)

    /**
      * Removes an item based on the item
      * @param item to be removed from the itemset
      * @tparam I the type of the item to be removed
      * @return a new T, where T is an ItemSet child with the item removed or identical if the item is not present
      */
    def remove[I <: Item[_]](item: I): T = removeByItem(this, item)

    /**
      * Function removes an item from the itemset c based on keyname
      * @param c the itemset to remove from
      * @param keyname the key name of the item to remove
      * @tparam I the Item type
      * @return a new T, where T is an ItemSet child with the item removed or identical if the item is not present
      */
    private def removeByKeyname[I <: Item[_]](c: ItemSet[T], keyname: String): T = {
      val f: Option[I] = getByKeyname(c.items, keyname)
      f match {
        case Some(item) => create(c.items.-(item))
        case None       => c.asInstanceOf[T] //create(c.items) also works
      }
    }

    /**
      * Function removes an item from the itemset c based on item content
      * @param c the itemset to remove from
      * @param itemIn the item that should be removed
      * @tparam I the Item type
      * @return a new T, where T is an ItemSet child with the item removed or identical if the item is not presen
      */
    private def removeByItem[I <: Item[_]](c: ItemSet[T], itemIn: I): T = {
      val f: Option[I] = getByItem(c.items, itemIn)
      f match {
        case Some(item) => create(c.items.-(item))
        case None       => c.asInstanceOf[T]
      }
    }

    // Function to find an item by keyname - made public to enable matchers
    private def getByKeyname[I](itemsIn: ItemsData, keyname: String): Option[I] =
      itemsIn.find(_.keyName == keyname).asInstanceOf[Option[I]]

    // Function to find an item by item
    private def getByItem[I](itemsIn: ItemsData, item: Item[_]): Option[I] =
      itemsIn.find(_.equals(item)).asInstanceOf[Option[I]]

    /**
      * Method called by subclass to create a copy with the same key (or other fields) and new items
      */
    protected def create(data: ItemsData): T

    protected def dataToString:String = items.mkString("(", ",", ")")

    override def toString = s"$typeName$dataToString"

    /**
      * Returns true if the data contains the given key
      */
    def contains(key: Key[_, _]): Boolean = items.exists(_.keyName == key.keyName)

    /**
      * Returns a set containing the names of any of the given keys that are missing in the data
      *
      * @param keys one or more keys
      */
    def missingKeys(keys: Key[_, _]*): Set[String] = {
      val argKeySet = keys.map(_.keyName).toSet
      val itemsKeySet = items.map(_.keyName)
      argKeySet.diff(itemsKeySet)
    }

    /**
      * java API: Returns a set containing the names of any of the given keys that are missing in the data
      *
      * @param keys one or more keys
      */
    @varargs
    def jMissingKeys(keys: Key[_, _]*): java.util.Set[String] = missingKeys(keys: _*).asJava

    /**
      * Returns a map based on this object where the keys and values are in string format
      * (Could be useful for exporting in a format that other languages can read).
      * Derived classes might want to add values to this map for fixed fields.
      */
    def getStringMap: Map[String, String] = items.map(i => i.keyName -> i.values.map(_.toString).mkString(",")).toMap
  }

  /**
    * This will include information related to the observation that is tied to an itemset
    * This will grow and develop.
    */
  case class ItemSetInfo(obsId: ObsId) {
    /**
      * Unique ID for this itemset
      */
    val runId: RunId = RunId()
  }

  object ItemSetInfo {
    implicit def apply(obsId: String): ItemSetInfo = ItemSetInfo(ObsId(obsId))
  }

  /**
    * Marker trait for sequence itemsets
    */
  sealed trait SequenceItemSet

  /**
    * Marker trait for control itemsets
    */
  sealed trait ControlItemSet

  /**
    * An itemset for setting telescope and instrument parameters
    *
    * @param itemsetKey identifies the target subsystem
    * @param items     an optional initial set of items (keys with values)
    */
  case class Setup(info: ItemSetInfo, itemsetKey: ItemSetKey, items: ItemsData = Set.empty[Item[_]])
    extends ItemSet[Setup] with ItemSetKeyData with SequenceItemSet with ControlItemSet {

    override def create(data: ItemsData) = Setup(info, itemsetKey, data)

    // This is here for Java to construct with String
    def this(info: ItemSetInfo, itemsetKey: String) = this(info, ItemSetKey.stringToItemSetKey(itemsetKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Item[_]](item: I): Setup = super.add(item)

    override def remove[S, I <: Item[S]](key: Key[S, I]): Setup = super.remove(key)
  }

  /**
    * An itemset for setting observation parameters
    *
    * @param itemsetKey identifies the target subsystem
    * @param items     an optional initial set of items (keys with values)
    */
  case class Observe(info: ItemSetInfo, itemsetKey: ItemSetKey, items: ItemsData = Set.empty[Item[_]])
    extends ItemSet[Observe] with ItemSetKeyData with SequenceItemSet with ControlItemSet {

    override def create(data: ItemsData) = Observe(info, itemsetKey, data)

    // This is here for Java to construct with String
    def this(info: ItemSetInfo, itemsetKey: String) = this(info, ItemSetKey.stringToItemSetKey(itemsetKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Item[_]](item: I): Observe = super.add(item)

    override def remove[S, I <: Item[S]](key: Key[S, I]): Observe = super.remove(key)
  }

  /**
    * An itemset indicating a pause in processing
    *
    * @param itemsetKey identifies the target subsystem
    * @param items     an optional initial set of items (keys with values)
    */
  case class Wait(info: ItemSetInfo, itemsetKey: ItemSetKey, items: ItemsData = Set.empty[Item[_]])
    extends ItemSet[Wait] with ItemSetKeyData with SequenceItemSet {

    override def create(data: ItemsData) = Wait(info, itemsetKey, data)

    // This is here for Java to construct with String
    def this(info: ItemSetInfo, itemsetKey: String) = this(info, ItemSetKey.stringToItemSetKey(itemsetKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Item[_]](item: I): Wait = super.add(item)

    override def remove[S, I <: Item[S]](key: Key[S, I]): Wait = super.remove(key)
  }

  /**
    * Filters
    */
  object ConfigFilters {
    // A filter type for various ConfigData
    type ConfigFilter[A] = A => Boolean

    def prefixes(configs: Seq[ItemSetKeyData]): Set[String] = configs.map(_.prefix).toSet

    def onlySetupConfigs(configs: Seq[SequenceItemSet]): Seq[Setup] = configs.collect { case ct: Setup => ct }

    def onlyObserveConfigs(configs: Seq[SequenceItemSet]): Seq[Observe] = configs.collect { case ct: Observe => ct }

    def onlyWaitConfigs(configs: Seq[SequenceItemSet]): Seq[Wait] = configs.collect { case ct: Wait => ct }

    val prefixStartsWithFilter: String => ConfigFilter[ItemSetKeyData] = query => sc => sc.prefix.startsWith(query)
    val prefixContainsFilter: String => ConfigFilter[ItemSetKeyData] = query => sc => sc.prefix.contains(query)
    val prefixIsSubsystem: Subsystem => ConfigFilter[ItemSetKeyData] = query => sc => sc.subsystem.equals(query)

    def prefixStartsWith(query: String, configs: Seq[ItemSetKeyData]): Seq[ItemSetKeyData] = configs.filter(prefixStartsWithFilter(query))

    def prefixContains(query: String, configs: Seq[ItemSetKeyData]): Seq[ItemSetKeyData] = configs.filter(prefixContainsFilter(query))

    def prefixIsSubsystem(query: Subsystem, configs: Seq[ItemSetKeyData]): Seq[ItemSetKeyData] = configs.filter(prefixIsSubsystem(query))
  }


  /**
    * Contains a list of itemsets that can be sent to a sequencer
    */
  final case class ItemsetList(itemsets: Seq[SequenceItemSet])

}
