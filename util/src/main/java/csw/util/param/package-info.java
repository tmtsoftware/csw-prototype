/**
 * <p>
 * <strong>Utility Classes</strong>
 * <p>
 * This project is intended to hold reusable utility classes used throughout the csw source code.
 * <p>
 * <strong>Commands and Events</strong>
 * <p>
 * This package contains classes and traits used for ''commands'' and ''events''.
 * These are all based on type-safe keys and values. Each key has a type member
 * and the key's values must be of that type.
 * <p>
 * <strong>Note</strong>: The param classes are implemented in Scala, so the automatically generated documentation is incomplete.
 * Please see the Scaladocs for this package for a more complete picture.
 * <p>
 * Commands and events are based on maps of keys and values, with some additional
 * information included, such as ids or timestamps.
 * <p>
 * The key/value store and event service make use of these classes, which need to be
 * serialized and deserialized for external storage (in Redis or Hornetq, for example).
 * The {@link csw.util.param.ParamSetSerializer.ParamSetSerializer} class provides support for this
 * (based on java serialization).
 * <p>
 * <strong>Scala and Java APIs</strong>
 * <p>
 * All the param and event classes are immutable. In Scala, the `set` methods return a new instance of the object with a
 * new item added and the `get` methods return an Option, in case the Key is not found. There are also `value` methods
 * that return a value directly, throwing an exception if the key or value is not found.
 * <p>
 * For the Java API, replace `get`, `set` and `value` with `jget`, `jset` and `jvalue`. These versions accept and
 * return Java types.
 * <p>
 * Note that internally, the Key and Item classes take two type parameters: S and J: The Scala and Java types.
 * This makes it easier to provide generic support for both languages.
 * <p>
 * <strong>Key Types</strong>
 * <p>
 * A set of standard key types and matching parameters are defined. Each key accepts one or more values
 * of the given type. The values are stored internally in a Vector:
 * <ul>
 * <li>IntKey</li>
 * <li>ShortKey</li>
 * <li>LongKey</li>
 * <li>FloatKey</li>
 * <li>DoubleKey</li>
 * <li>StringKey</li>
 * <li>CharKey</li>
 * <li>BooleanKey</li>
 * </ul>
 * The following keys support one or more values that are each one and two dimensional arrays (stored internally as Vectors):
 * <ul>
 * <li>DoubleVectorKey</li>
 * <li>DoubleMatrixKey</li>
 * <li>IntVectorKey</li>
 * <li>IntMatrixKey</li>
 * </ul>
 * In addition there is a GenericKey class that can be used for custom types. It is however recommended to
 * use only the standard key types, in oder to ensure that binary and JSON serialization and deserialization
 * works everywhere.
 * <p>
 * Example:
 * <p>
 * <pre> {@code
 *   val commandInfo = CommandInfo(ObsId("001"))
 *
 *   // Define a key for an event id
 *   val eventNum = IntKey("eventNum")
 *
 *   val exposureTime = DoubleKey("exposureTime")
 *
 *   // Define a key for image data
 *   val imageData = IntVectorKey("imageData")
 *
 *   // Dummy image data
 *   val testImageData = IntArray(Array.ofDim[Int](10000).toVector)
 *
 *   val prefix = "tcs.mobie.red.dat.exposureInfo"
 *
 *   // ...
 *
 *   val sc = Setup(commandInfo, prefix)
 *       .set(eventNum, num)
 *       .set(exposureTime, 1.0)
 *       .set(imageData, testImageData)
 * } </pre>
 * <p>
 * Java Example:
 * <p>
 * <pre> {@code
 *     Parameters.CommandInfo info = new CommandInfo("Obs001");
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
 *     Setup sc = new Setup(commandInfo, prefix)
 *            .jset(eventNum, num)
 *            .jset(exposureTime, 1.0)
 *            .jset(imageData, testImageData);
 *
 * } </pre>
 * <p>
 * <strong>Two Dimensional Arrays</strong>
 * <p>
 * Scala Example:
 * <p>
 * <pre> {@code
 *       val k1 = DoubleMatrixKey("myMatrix")
 *       val m1 = DoubleMatrix(Vector(
 *         Vector(1.0, 2.0, 3.0),
 *         Vector(4.1, 5.1, 6.1),
 *         Vector(7.2, 8.2, 9.2)
 *       ))
 *       val sc1 = Setup(commandInfo, ck).set(k1, m1)
 *       assert(jvalue(jitem(sc1, k1)) == m1)
 * } </pre>
 * <p>
 * Java Example:
 * <p>
 * <pre> {@code
 *         DoubleMatrixKey k1 = new DoubleMatrixKey("myMatrix");
 *         JDoubleMatrix m1 = new JDoubleMatrix(Arrays.asList(
 *                 Arrays.asList(1.0, 2.0, 3.0),
 *                 Arrays.asList(4.1, 5.1, 6.1),
 *                 Arrays.asList(7.2, 8.2, 9.2)));
 *         Setup sc1 = new Setup(commandInfo, ck).jset(k1, m1);
 *         assert (sc1.jvalue(k1).equals(m1));
 * } </pre>
 */
package csw.util.param;