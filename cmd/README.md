Command Service
=====================

This module implements the Command Service, which is used to send commands to actors.

Main Packages:
--------------

* akka - the Akka actor interface (based on core)
* core - the core classes (like the Configuration class)
* spray - The spray REST/HTTP interface


HCD (Hardware Control Device) View
----------------------------------

The command service actor receives a submit command with the config
(or other control commands) and passes it to the command queue actor.

The command queue actor tells the command queue controller actor that
there is work available.  This actor comes in various flavors so that
it can implement "one at a time" behavior or concurrent behavior. The
HCD or Assembly class can extend a trait that adds the correct queue
controller actor to the system. The queue controller actor also
receives command status info and uses that to decide when the next
config should be taken from the queue and passed to the "queue
client".  It does this by sending a "Dequeue" message to the queue
actor. The queue actor then sends the config to the queue client. In
the case of an HCD, the queue client is the config actor (For an
assembly it is the config distributor actor).

When the config actor receives the submit, it performs the work and
then sends the status to the command status actor.

The command status actor passes the status to subscribers (which
include the queue controller) and also to the original submitter of
the config (The sender is passed along with the submit message).

![HCD Actor graph](doc/HCD.jpg)


Multi-Axis HCDs
---------------

Some multi-axis HCDs can do multiple things at once, but each axis can
only do one thing at a time. For this case there is the MultiAxisCommandServiceActor
and the MultiAxisOneAtATimeCommandQueueController.

The actor graph for multi-axis HCDs is similar to the one for plain HCDs,
except that there is one copy of the
Command Queue, Command Queue Controller, Command Status and Config actors for each axis.


Assembly View
-------------

The assembly has the same basic setup, except that here you have a
config registration actor, which receives registration messages from
the HCDs. Here you also have the config distributor actor, which
subscribes to the registration information, which includes the HCD
actor refs along with the set of config keys each HCD is interested
in.

When a config is submitted to an assembly, it is passed to the command
queue actor, which then passes it to the config distributor actor. It
uses the registry info to pass parts of the config to the different
HCDs.  Later, the "command status actor" in each "HCD" sends the
command status to the config distributor actor (since it appears as
the original sender in the message sent to the HCDs). When all the
HCDs (or assemblies) involved are done, the final command status is
sent to the assembly's command status actor, which forwards it to the
subscribers and the original submitter.

![Assembly Actor graph](doc/assembly.jpg)

