Integration Test for Container, Assembly and HCD Packaging
==========================================================

This directory contains standalone applications for testing the Container, Assembly and HCD components and is based on
the document "OSW TN009 - TMT CSW PACKAGING SOFTWARE DESIGN DOCUMENT".

To compile, type "sbt" and then:

* project container1
* dist
* project container2
* dist

To run: Open terminal windows in the directories: container2/target/bin and container1/target/bin and type "./start" in each.
(Currently container2 has to be started first, but that will be changed.)
