Test Application
================

This directory contains a test application that fetches (or creates) a configuration from the
config service and then sends it as a command to the command service.

The config actors that receive the command can run on a separate host.
By default the test runs on two java VMs on the local host, but can be configured to
run on separate hosts by editing an IP address in the client config file (resources/reference.conf).

Build it
--------

To build it run "sbt dist".
The application is then insalled under csw/target/test-app and csw/target/test-client.

Run it
------

Start the test-app first by typing 'start' in target/test-app/bin.
Then start the client by typing 'start' in target/test-client/bin.

The log messages indicate that one config command was sent.
After this both applications exit.

