package csw.util

/**
 * == Utility Classes ==
 *
 * This project is intended to hold reusable utility classes used throughout the csw source code.
 *
 * == Configurations and Events ==
 *
 * This package contains classes and traits used for ''configurations'' and ''events''.
 * These are all based on type-safe keys and values. Each key has a type member
 * and the key's values must be of that type.
 *
 * Configurations and events are based on maps of keys and values, with some additional
 * information included, such as ids or timestamps.
 *
 * The key/value store and event service make use of these classes, which need to be
 * serialized and deserialized for external storage (in Redis or Hornetq, for example).
 * The [[csw.util.config.ConfigSerializer.ConfigSerializer]] class provides support for this
 * (based on java serialization).
 *
 * == Scala and Java APIs ==
 *
 * All the config and event classes are immutable. In Scala, the `set` methods return a new instance of the object with a
 * new item added and the `get` methods return an Option, in case the Key is not found. There are also `value` methods
 * that return a value directly, throwing an exception if the key or value is not found.
 *
 * For the Java API, replace `get`, `set` and `value` with `jget`, `jset` and `jvalue`. These versions accept and
 * return Java types.
 *
 * Note that internally, the Key and Item classes take two type parameters: S and J: The Scala and Java types.
 * This makes it easier to provide generic support for both languages.
 *
 * == Key Types ==
 *
 * A set of standard key types and matching items are defined. Each key accepts one or more values
 * of the given type. The values are stored internally in a Vector:
 *
 * - IntKey
 * - ShortKey
 * - LongKey
 * - FloatKey
 * - DoubleKey
 * - StringKey
 * - CharKey
 * - BooleanKey
 *
 * The following keys support one or more values that are each one and two dimensional arrays (stored internally as Vectors):
 *
 * - DoubleVectorKey
 * - DoubleMatrixKey
 * - IntVectorKey
 * - IntMatrixKey
 *
 * In addition there is a GenericKey class that can be used for custom types. It is however recommended to
 * use only the standard key types, in oder to ensure that binary and JSON serialization and deserialization
 * works everywhere.
 *
 * Example:
 *
 * {{{
 *   // Define a key for an event id
 *   val eventNum = IntKey("eventNum")
 *
 *   val exposureTime = DoubleKey("exposureTime")
 *
 *   // Define a key for image data
 *   val imageData = IntVectorKey("imageData")
 *
 *   // Dummy image data
 *   val testImageData = IntVector(Array.ofDim[Int](10000).toVector)
 *
 *   val prefix = "tcs.mobie.red.dat.exposureInfo"
 *
 *   // ...
 *
 *   ObserveEvent(prefix)
 *       .set(eventNum, num)
 *       .set(exposureTime, 1.0)
 *       .set(imageData, testImageData)
 * }}}
 *
 * Java Example:
 *
 * {{{
 *     static final DoubleKey exposureTime = new DoubleKey("exposureTime");
 *
 *     // Define a key for an event id
 *     static final IntKey eventNum = new IntKey("eventNum");
 *
 *     // Define a key for image data
 *     static final IntVectorKey imageData = new IntVectorKey("imageData");
 *
 *     // Dummy image data
 *     static final JIntVector testImageData = JIntVector.fromArray(new int[10000]);
 *
 *     // Prefix to use for the event
 *     static final String prefix = "tcs.mobie.red.dat.exposureInfo";
 *
 *     // ...
 *
 *        return new ObserveEvent(prefix)
 *            .jset(eventNum, num)
 *            .jset(exposureTime, 1.0)
 *            .jset(imageData, testImageData);
 *
 * }}}
 *
 * Combining Configs
 * -----------------
 *
 * In some cases you may need to wrap multiple configs, for example to pass to an assembly.
 *
 * {{{
 *     val encoder1 = IntKey("encoder1")
 *     val encoder2 = IntKey("encoder2")
 *     val xOffset = IntKey("xOffset")
 *     val yOffset = IntKey("yOffset")
 *     val obsId = "Obs001"
 *
 *     val sc1 = ObserveConfig(ck1).set(encoder1, 22).set(encoder2, 33)
 *     val sc2 = ObserveConfig(ck1).set(xOffset, 1).set(yOffset, 2)
 *     val configArg = ObserveConfigArg(obsId, sc1, sc2)
 * }}}
 *
 * Java API:
 *
 * {{{
 *     IntKey encoder1 = new IntKey("encoder1");
 *     IntKey encoder2 = new IntKey("encoder2");
 *     IntKey xOffset = new IntKey("xOffset");
 *     IntKey yOffset = new IntKey("yOffset");
 *     String obsId = "Obs001";
 *
 *     ObserveConfig sc1 = new ObserveConfig(ck1).jset(encoder1, 22).jset(encoder2, 33);
 *     ObserveConfig sc2 = new ObserveConfig(ck1).jset(xOffset, 1).jset(yOffset, 2);
 *     ObserveConfigArg configArg = Configurations.createObserveConfigArg(obsId, sc1, sc2);
 * }}}
 */
package object config {

}
