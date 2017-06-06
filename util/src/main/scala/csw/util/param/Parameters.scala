package csw.util.param

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.language.implicitConversions

/**
  * Support for sets of generic, type-safe command or event parameters
  * (key/value objects with units)
  */
object Parameters {

  /**
    * Combines subsystem and the subsystem's prefix
    *
    * @param subsystem the subsystem that is the target of the parameter set
    * @param prefix    the subsystem's prefix
    */
  case class Prefix(subsystem: Subsystem, prefix: String) {
    override def toString = s"[$subsystem, $prefix]"
  }

  /**
    * A top level key for a parameter set: combines subsystem and the subsystem's prefix
    */
  object Prefix {
    private val SEPARATOR = '.'

    /**
      * Creates a Prefix from the given string
      *
      * @return an Prefix object parsed for the subsystem and prefix
      */
    implicit def stringToPrefix(prefix: String): Prefix = Prefix(subsystem(prefix), prefix)

    private def subsystem(keyText: String): Subsystem = {
      assert(keyText != null)
      Subsystem.lookup(keyText.splitAt(keyText.indexOf(SEPARATOR))._1).getOrElse(Subsystem.BAD)
    }
  }

  type ParameterSet = Set[Parameter[_]]

  /**
    * A trait to be mixed in that provides a parameter set, Key item, subsystem and prefix
    */
  trait ParameterSetKeyData {
    self: ParameterSetType[_] =>
    /**
      * Returns an object providing the subsystem and prefix for the parameter set
      */
    def prefix: Prefix

    /**
      * The subsystem for the parameter set
      */
    final def subsystem: Subsystem = prefix.subsystem

    /**
      * The prefix for the parameter set
      */
    final def prefixStr: String = prefix.prefix

    override def toString = s"$typeName[$subsystem, $prefixStr]$dataToString"
  }

  /**
    * The base trait for various parameter set types (commands or events)
    *
    * @tparam T the subclass of ParameterSetType
    */
  trait ParameterSetType[T <: ParameterSetType[T]] {
    self: T =>

    /**
      * A name identifying the type of parameter set, such as "setup", "observe".
      * This is used in the JSON and toString output.
      */
    def typeName: String = getClass.getSimpleName

    /**
      * Holds the items for this parameter set
      */
    def items: ParameterSet

    /**
      * The number of items in this parameter set
      *
      * @return the number of items in the parameter set
      */
    def size: Int = items.size

    /**
      * Adds a parameter to the parameter set
      *
      * @param item the item to add
      * @tparam I the Item type
      * @return a new instance of this parameter set with the given item added
      */
    def add[I <: Parameter[_]](item: I): T = doAdd(this, item)

    private def doAdd[I <: Parameter[_]](c: T, item: I): T = {
      val itemSetRemoved: T = removeByKeyname(c, item.keyName)
      create(itemSetRemoved.items + item)
    }

    /**
      * Adds several items to the parameter set
      *
      * @param itemsToAdd the list of items to add to the parameter set
      * @tparam I must be a subclass of Item
      * @return a new instance of this parameter set with the given item added
      */
    def madd[I <: Parameter[_]](itemsToAdd: I*): T = itemsToAdd.foldLeft(this)((c, item) => doAdd(c, item))

    /**
      * Returns an Option with the item for the key if found, otherwise None
      *
      * @param key the Key to be used for lookup
      * @return the item for the key, if found
      * @tparam S the Scala value type
      * @tparam I the item type for the Scala value S
      */
    def get[S, I <: Parameter[S]](key: Key[S, I]): Option[I] = getByKeyname[I](items, key.keyName)

    /**
      * Returns an Option with the item for the key if found, otherwise None. Access with keyname rather
      * than Key
      *
      * @param keyName the keyname to be used for the lookup
      * @tparam I the value type
      */
    def getByName[I <: Parameter[_]](keyName: String): Option[I] = getByKeyname[I](items, keyName)

    def find[I <: Parameter[_]](item: I): Option[I] = getByKeyname[I](items, item.keyName)

    /**
      * Return the item associated with a Key rather than an Option
      *
      * @param key the Key to be used for lookup
      * @tparam S the Scala value type
      * @tparam I the Item type associated with S
      * @return the item associated with the Key or a NoSuchElementException if the key does not exist
      */
    final def apply[S, I <: Parameter[S]](key: Key[S, I]): I = get(key).get

    /**
      * Returns the actual item associated with a key
      *
      * @param key the Key to be used for lookup
      * @tparam S the Scala value type
      * @tparam I the Item type associated with S
      * @return the item associated with the key or a NoSuchElementException if the key does not exist
      */
    final def item[S, I <: Parameter[S]](key: Key[S, I]): I = get(key).get

    /**
      * Returns true if the key exists in the parameter set
      *
      * @param key the key to check for
      * @return true if the key is found
      * @tparam S the Scala value type
      * @tparam I the type of the Item associated with the key
      */
    def exists[S, I <: Parameter[S]](key: Key[S, I]): Boolean = get(key).isDefined

    /**
      * Remove a parameter from the parameter set by key
      *
      * @param key the Key to be used for removal
      * @tparam S the Scala value type
      * @tparam I the item type used with Scala type S
      * @return a new T, where T is a parameter set child with the key removed or identical if the key is not present
      */
    def remove[S, I <: Parameter[S]](key: Key[S, I]): T = removeByKeyname(this, key.keyName) //doRemove(this, key)

    /**
      * Removes a parameter based on the item
      *
      * @param item to be removed from the parameter set
      * @tparam I the type of the item to be removed
      * @return a new T, where T is a parameter set child with the item removed or identical if the item is not present
      */
    def remove[I <: Parameter[_]](item: I): T = removeByItem(this, item)

    /**
      * Function removes a parameter from the parameter set c based on keyname
      *
      * @param c       the parameter set to remove from
      * @param keyname the key name of the item to remove
      * @tparam I the Item type
      * @return a new T, where T is an parameter set child with the item removed or identical if the item is not present
      */
    private def removeByKeyname[I <: Parameter[_]](c: ParameterSetType[T], keyname: String): T = {
      val f: Option[I] = getByKeyname(c.items, keyname)
      f match {
        case Some(item) => create(c.items.-(item))
        case None => c.asInstanceOf[T] //create(c.items) also works
      }
    }

    /**
      * Function removes an item from the parameter set c based on item content
      *
      * @param c      the parameter set to remove from
      * @param itemIn the item that should be removed
      * @tparam I the Item type
      * @return a new T, where T is an parameter set child with the item removed or identical if the item is not presen
      */
    private def removeByItem[I <: Parameter[_]](c: ParameterSetType[T], itemIn: I): T = {
      val f: Option[I] = getByItem(c.items, itemIn)
      f match {
        case Some(item) => create(c.items.-(item))
        case None => c.asInstanceOf[T]
      }
    }

    // Function to find an item by keyname - made public to enable matchers
    private def getByKeyname[I](itemsIn: ParameterSet, keyname: String): Option[I] =
      itemsIn.find(_.keyName == keyname).asInstanceOf[Option[I]]

    // Function to find an item by item
    private def getByItem[I](itemsIn: ParameterSet, item: Parameter[_]): Option[I] =
      itemsIn.find(_.equals(item)).asInstanceOf[Option[I]]

    /**
      * Method called by subclass to create a copy with the same key (or other fields) and new items
      */
    protected def create(data: ParameterSet): T

    protected def dataToString: String = items.mkString("(", ",", ")")

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
    * This will include information related to the observation that is tied to an parameter set
    * This will grow and develop.
    *
    * @param obsId the observation id
    * @param runId unique ID for this parameter set
    */
  case class CommandInfo(obsId: ObsId, runId: RunId = RunId()) {

    /**
      * Creates an instance with the given obsId and a unique runId
      */
    def this(obsId: String) = this(ObsId(obsId))
  }

  object CommandInfo {
    implicit def strToItemSetInfo(obsId: String): CommandInfo = CommandInfo(ObsId(obsId))
  }

  /**
    * Trait for sequence parameter sets
    */
  sealed trait SequenceCommand

  /**
    * Marker trait for control parameter sets
    */
  sealed trait ControlCommand extends SequenceCommand {
    /**
      * A name identifying the type of parameter set, such as "setup", "observe".
      * This is used in the JSON and toString output.
      */
    def typeName: String

    /**
      * information related to the parameter set
      */
    val info: CommandInfo

    /**
      * identifies the target subsystem
      */
    val itemSetKey: Prefix

    /**
      * an optional initial set of items (keys with values)
      */
    val items: ParameterSet
  }


  /**
    * a parameter set for setting telescope and instrument parameters
    *
    * @param info       information related to the parameter set
    * @param prefix identifies the target subsystem
    * @param items      an optional initial set of items (keys with values)
    */
  case class Setup(info: CommandInfo, prefix: Prefix, items: ParameterSet = Set.empty[Parameter[_]])
    extends ParameterSetType[Setup] with ParameterSetKeyData with SequenceCommand with ControlCommand {

    override def create(data: ParameterSet) = Setup(info, prefix, data)

    // This is here for Java to construct with String
    def this(info: CommandInfo, itemSetKey: String) = this(info, Prefix.stringToPrefix(itemSetKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Parameter[_]](item: I): Setup = super.add(item)

    override def remove[S, I <: Parameter[S]](key: Key[S, I]): Setup = super.remove(key)
  }

  /**
    * a parameter set for setting observation parameters
    *
    * @param info       information related to the parameter set
    * @param prefix identifies the target subsystem
    * @param items      an optional initial set of items (keys with values)
    */
  case class Observe(info: CommandInfo, prefix: Prefix, items: ParameterSet = Set.empty[Parameter[_]])
    extends ParameterSetType[Observe] with ParameterSetKeyData with SequenceCommand with ControlCommand {

    override def create(data: ParameterSet) = Observe(info, prefix, data)

    // This is here for Java to construct with String
    def this(info: CommandInfo, itemSetKey: String) = this(info, Prefix.stringToPrefix(itemSetKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Parameter[_]](item: I): Observe = super.add(item)

    override def remove[S, I <: Parameter[S]](key: Key[S, I]): Observe = super.remove(key)
  }

  /**
    * a parameter set indicating a pause in processing
    *
    * @param info       information related to the parameter set
    * @param prefix identifies the target subsystem
    * @param items      an optional initial set of items (keys with values)
    */
  case class Wait(info: CommandInfo, prefix: Prefix, items: ParameterSet = Set.empty[Parameter[_]])
    extends ParameterSetType[Wait] with ParameterSetKeyData with SequenceCommand {

    override def create(data: ParameterSet) = Wait(info, prefix, data)

    // This is here for Java to construct with String
    def this(info: CommandInfo, itemSetKey: String) = this(info, Prefix.stringToPrefix(itemSetKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Parameter[_]](item: I): Wait = super.add(item)

    override def remove[S, I <: Parameter[S]](key: Key[S, I]): Wait = super.remove(key)
  }

  /**
    * A parameters set for returning results
    *
    * @param info       information related to the parameter set
    * @param prefix identifies the target subsystem
    * @param items      an optional initial set of items (keys with values)
    */
  case class Result(info: CommandInfo, prefix: Prefix, items: ParameterSet = Set.empty[Parameter[_]])
    extends ParameterSetType[Result] with ParameterSetKeyData {

    override def create(data: ParameterSet) = Result(info, prefix, data)

    // This is here for Java to construct with String
    def this(info: CommandInfo, itemSetKey: String) = this(info, Prefix.stringToPrefix(itemSetKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Parameter[_]](item: I): Result = super.add(item)

    override def remove[S, I <: Parameter[S]](key: Key[S, I]): Result = super.remove(key)
  }

  /**
    * Filters
    */
  object ParameterSetFilters {
    // A filter type for various parameter set data
    type ItemSetFilter[A] = A => Boolean

    def prefixes(itemSets: Seq[ParameterSetKeyData]): Set[String] = itemSets.map(_.prefixStr).toSet

    def onlySetups(itemSets: Seq[SequenceCommand]): Seq[Setup] = itemSets.collect { case ct: Setup => ct }

    def onlyObserves(itemSets: Seq[SequenceCommand]): Seq[Observe] = itemSets.collect { case ct: Observe => ct }

    def onlyWaits(itemSets: Seq[SequenceCommand]): Seq[Wait] = itemSets.collect { case ct: Wait => ct }

    val prefixStartsWithFilter: String => ItemSetFilter[ParameterSetKeyData] = query => sc => sc.prefixStr.startsWith(query)
    val prefixContainsFilter: String => ItemSetFilter[ParameterSetKeyData] = query => sc => sc.prefixStr.contains(query)
    val prefixIsSubsystem: Subsystem => ItemSetFilter[ParameterSetKeyData] = query => sc => sc.subsystem.equals(query)

    def prefixStartsWith(query: String, itemSets: Seq[ParameterSetKeyData]): Seq[ParameterSetKeyData] = itemSets.filter(prefixStartsWithFilter(query))

    def prefixContains(query: String, itemSets: Seq[ParameterSetKeyData]): Seq[ParameterSetKeyData] = itemSets.filter(prefixContainsFilter(query))

    def prefixIsSubsystem(query: Subsystem, itemSets: Seq[ParameterSetKeyData]): Seq[ParameterSetKeyData] = itemSets.filter(prefixIsSubsystem(query))
  }

  /**
    * Contains a list of parameter sets that can be sent to a sequencer
    */
  final case class CommandList(itemSets: Seq[SequenceCommand])

}
