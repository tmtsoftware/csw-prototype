Utility Classes
===============

This project is intended to hold reusable utility classes used throughout the csw source code.

Configurations and Events
--------------------------

This project contains classes and traits used for *configurations* and *events*.
These are all based on type-safe keys and items (a set of values with optional units). 
Each key has a specific type and the key's values must be of that type.

There are a variety of similar classes, used for different purposes:

Configurations:

* SetupConfig
* ObserveConfig

Events:

* StatusEvent
* ObserveEvent
* SystemEvent

State Variables:

* CurrentState
* DemandState

The key/value store and event service make use of these classes, which need to be
serialized and deserialized for external storage (in Redis or Hornetq, for example).
The (ConfigSerializer)[src/main/scala/csw/util/config/ConfigSerializer.scala] class provides support for this.

Scala and Java APIs
-------------------

All the config and event classes are immutable. The `set` methods return a new instance of the object with a
new item added and the `get` methods return an Option, in case the Key is not found. There are also `value` methods
that return a value directly, throwing an exception if the key or value is not found.

Key Types
---------

A set of standard key types and matching items are defined. Each key accepts one or more values
of the given type. The values are stored internally in a Vector:

* IntKey
* ShortKey
* LongKey
* FloatKey
* DoubleKey
* StringKey
* CharKey
* BooleanKey

The following keys support one or more values that are each one or two dimensional arrays (stored internally as Arrays):

* IntArrayKey, IntMatrixKey
* ShortArrayKey, ShortMatrixKey
* LongArrayKey, LongMatrixKey
* FloatArrayKey, FloatMatrixKey
* DoubleArrayKey, DoubleMatrixKey

In addition there is a GenericKey class that can be used for custom types. It is however recommended to
use only the standard key types, in oder to ensure that binary and JSON serialization and deserialization
works everywhere.

Example:

```
  // Define a key for an event id
  val eventNum = IntKey("eventNum")

  val exposureTime = DoubleKey("exposureTime")

  // Define a key for image data
  val imageData = IntArrayKey("imageData")

  // Dummy image data
  val testImageData = IntArray(Array.ofDim[Int](10000))

  val prefix = "tcs.mobie.red.dat.exposureInfo"

  // ...

    val config = SetupConfig(prefix)
      .add(eventNum.set(num))
      .add(exposureTime.set(1.0))
      .add(imageData.set(testImageData))
      
  // Or you can use the Scala DSL to do the same thing:
  
    import csw.util.config.ConfigDSL._
    
    sc(prefix,
       eventNum -> num,
       exposureTime -> 1.0,
       imageData -> testImageData)

```

Java Example:

```
    import static javacsw.util.config.JItems.*;
    import static javacsw.util.config.JConfigDSL.*;

    static final DoubleKey exposureTime = new DoubleKey("exposureTime");

    // Define a key for an event id
    static final IntKey eventNum = new IntKey("eventNum");

    // Define a key for image data
    static final IntArrayKey imageData = new IntArrayKey("imageData");

    // Dummy image data
    static final JIntArray testImageData = JIntArray.fromArray(new int[10000]);

    // Prefix to use for the event
    static final String prefix = "tcs.mobie.red.dat.exposureInfo";

    // ...

      SetupConfig config = new SetupConfig(prefix)
        .add(jset(eventNum, num))
        .add(jset(exposureTime, 1.0))
        .add(jset(imageData, testImageData));

      // Alternative syntax
      SetupConfig config = jadd(sc(prefix,
        jset(eventNum, num),
        jset(exposureTime, 1.0),
        jset(imageData, testImageData));
```

Scala Example using a matrix as the key value:
```
    val k1 = DoubleMatrixKey("myMatrix")
    val dm1 = DoubleMatrix(Array(Array[Double](1, 2, 3), Array[Double](2, 3, 6), Array[Double](4, 6, 12)))
    val sc1 = sc("test", k1 -> dm1 withUnits UnitsOfMeasure.Deg)
    assert(setupConfig1.get(k1).get.head(0, 0) == 1)
```

Java Example:

```
   final DoubleMatrixKey k1 = DoubleMatrixKey("matrixTest");
   double[][] m1 = {{1., 2., 3.}, {4., 5., 6.}, {7., 8., 9.}};
   DoubleMatrix dm1 = DoubleMatrix(m1);
   SetupConfig sc1 = jadd(sc("test"), jset(k1, dm1));
   assertTrue(jvalue(jitem(sc1, k1)).apply(0, 0) == 1);
```

Combining Configs
-----------------

In some cases you may need to wrap multiple configs, for example to pass to an assembly.

```
    val encoder2 = IntKey("encoder2")
    val xOffset = IntKey("xOffset")
    val yOffset = IntKey("yOffset")
    val obsId = "Obs001"

    val sc1 = SetupConfig(ck1).madd(encoder1.set(22), encoder2.set(33))
    val sc2 = SetupConfig(ck1).madd(xOffset.set(1), yOffset.set(2))
    val configArg = SetupConfigArg(obsId, sc1, sc2)
    
    assert(configArg.info.obsId.obsId == obsId)
    assert(configArg.configs.toList == List(sc1, sc2))
```

Java API:

```
    IntKey encoder1 = IntKey("encoder1");
    IntKey encoder2 = IntKey("encoder2");
    IntKey xOffset = IntKey("xOffset");
    IntKey yOffset = IntKey("yOffset");
    String obsId = "Obs001";
    
    SetupConfig sc1 = jadd(sc(ck1), jset(encoder1, 22), jset(encoder2, 33));
    SetupConfig sc2 = jadd(sc(ck1), jset(xOffset, 1), jset(yOffset, 2));
    SetupConfigArg configArg = Configurations.createSetupConfigArg(obsId, sc1, sc2);
    
    assertTrue(configArg.info().obsId().obsId().equals(obsId));
    assertTrue(configArg.jconfigs().equals(Arrays.asList(sc1, sc2)));
```