Java API for the Key Value Store
================================

See the [kvs](../kvs) project for an overview.

Provides key/value store and publish/subscribe features based on Redis (http://redis.io/).
An event of configuration object can be set or published on a channel and subscribers
can receive the events. The last n events are always saved for reference (where n is an optional argument).

Note that the tests assume the redis server is running.

The [IBlockingKeyValueStore](src/main/java/javacsw/services/kvs/IBlockingKeyValueStore.java) class provides factory methods to create
a key/value store for various event and config types. This API blocks while waiting for replies from
the server.

The [IKeyValueStore](src/main/java/javacsw/services/kvs/IKeyValueStore.java) class also provides factory methods to create
a key/value store for various event and config types, but provides a non-blocking API based on futures.

The following example shows how to create a `SetupConfig` and store it in
the kvs:

```
SetupConfig config1 = JConfigurations.createSetupConfig("tcs.test")
.set(infoValue, 1)
.set(infoStr, "info 1")
.configType();

kvs.set("test1", config1);
Optional<SetupConfig> val1Opt = kvs.get("test1");
assert(val1Opt.isPresent());
SetupConfig val1 = val1Opt.get();
assert(val1.prefix().equals("tcs.test"));
assert(val1.get(infoValue).contains(1));
assert(val1.get(infoStr).contains("info 1"));
```