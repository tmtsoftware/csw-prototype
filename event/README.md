Event Service
=============

This project implements an event service based on Akka and HornetQ.

Event Publisher
---------------

The EventService class provides the method `publish(event)` to publish an object of type `Event` 
(a type alias for `EventServiceEvent`).

Event Subscriber
----------------

The EventSubscriber trait adds the method `subscribe(prefix)`. After calling this method, the actor
will receive all Event messages published for the prefix. You can use wildcards in the prefix string.
For example `tmt.mobie.red.dat.*` or `tmt.mobie.red.dat.#`, where `*` matches a single word and `#` matches
multiple words separated by dots. Subscribers should be careful not to block when receiving messages,
so that the actor queue does not fill up too much.

Performance Test
----------------

To run the performance test, you need to install hornetq-2.4.0.Final and start it with bin/run.sh.

The default config file used is:

    hornetq-2.4.0.Final/config/stand-alone/non-clustered/hornetq-configuration.xml
    
By default Hornetq is configured to put messages in memory and block the publisher if the queue gets full.
The test is designed to notice that and report an error. The goal of the test is to find out
how fast events can be published. The speed is controlled by the `delay` setting at the top
of `EventPubSubTest.scala`. The goal is to try to find a value for delay that lets the test 
run without the publisher hanging or Hornetq running out of memory. The test does some handshaking
between the publisher and subscriber to try to make sure the queues don't fill up.

You can also edit the Hornetq config file and replace BLOCK with PAGE, to avoid running out
of memory, however, in a performance test, what will happed is that the paging directory 
(hornetq-2.4.0.Final/data) will just grow without bounds.

You can also edit the expiration time of events (default 1 second), however making this too
small will cause the subscriber to miss messages.

You can run the test with:

  sbt "project event" test

If errors occur, try increasing the `delay` setting.
