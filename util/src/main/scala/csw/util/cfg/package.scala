package csw.util

/**
 * This package contains classes and traits used for *configurations* and *events*.
 * These are all based on type-safe keys and values. Each key has a type member
 * and the key's values must be of that type.
 *
 * Configurations and events are based on maps of keys and values, with some additional
 * information included, such as ids or timestamps.
 *
 * The key/value store and event service make use of these classes, which need to be
 * serialized and deserialized for external storage (in Redis or Hornetq, for example).
 * The [[csw.util.cfg.ConfigSerializer.ConfigSerializer]] class provides support for this
 * (based on java serialization).
 *
 * Example:
 *
 * {{{
 * // Define a key for an event number (Just for testing)
 * val eventNum = Key.create[Int]("eventNum")
 *
 * // Define a key for some image data
 * val imageData = Key.create[Array[Short]]("imageData")
 *
 * // Dummy image data
 * val testImageData = Array.ofDim[Short](10000)
 * // ...
 * val prefix = "tcs.mobie.red.dat.exposureInfo"
 *
 * val event = ObserveEvent(prefix)
 * .set(eventNum, num)
 * .set(exposureTime, 1.0)
 * .set(imageData, testImageData)
 * }}}
 *
 */
package object cfg {

}
