Protobuf Serialization
======================

The `events.proto` file is used to generate a Google Protobuf from an event.
The command to generate the Java class is:

```
protoc --java_out ../java/ events.proto
```

This creates ../java/csw/util/cfg/Protobuf.java.
