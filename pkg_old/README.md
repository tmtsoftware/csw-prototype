Component Packaging (pkg)
=========================

This project provides classes for packaging (Container, Assembly, HCD), as described in
"OSW TN009 - TMT CSW PACKAGING SOFTWARE DESIGN DOCUMENT".

The multi-jvm directory is used by the sbt-multi-jvm plugin to run tests with multiple JVMs.
(Note: For Idea, you currently have to add multi-jvm/scala as a test source directory in order to use the IDE features there.)

To compile and run the multi-jvm tests from the sbt console, type: "project pkg" and then "multi-jvm:test".

Using the Sequencer Application
-------------------------------

One way to test containers, components and lifecycle commands manually is with
the [sequencer](../apps/sequencer/README.md) shell. That is a scala REPL, with
special commands defined for working with containers and components.






