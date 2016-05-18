CSW Java APIs
=============

This project provides Java (8) APIs for the CSW classes.
These are thin wrappers, implemented in Scala and Java, that translate between
Scala and Java types. Java APIs are provided for all the CSW projects.

Java APIs for ccs
-----------------

* JAssemblyController - parent class for an actor that accepts configurations for an Assembly and communicates with
                        one or more HCDs or other assemblies before replying with a command status.
                        Note: You probably want to use
                        [JAssemblyControllerWithLifecycleHandler](src/main/java/javacsw/services/pkg/JHcdControllerWithLifecycleHandler.java)
                        instead, since it adds in supervisor/lifecycle support.

* JHcdController

* JHcdStatusMatcherActorFactory


