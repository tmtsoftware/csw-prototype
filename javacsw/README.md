CSW Java APIs
=============

This project provides Java (8) APIs for the CSW classes.
These are thin wrappers, implemented in Scala and Java, that translate between
Scala and Java types. Java APIs are provided for all the CSW subprojects:

* [Command and Control Service (ccs)](README.ccs.md)

* [Config Service (cs)](README.cs.md)

* [Event Service (event)](README.event.md)

* [Key Value Store (kvs)](README.kvs.md)

* [Location Service (loc)](README.loc.md)

* [Component Packaging (pkg)](README.pkg.md)

* [Time Service (ts)](README.ts.md)

* [Utilities (util)](../util/README.md)


See the generated Java documentation for more details. After running the csw install.sh script, the Java API docs
can be found under the $csw/../install/doc/java directory (where $csw is the top level csw sources directory).

Note that for API classes that are implemented in Scala, the generated Scala documentation may contain
more detailed information. The Scala docs are installed under $csw/../install/doc/scala directory
