Event Service
=============

This projects implements an event service based on Akka and HornetQ.
An event service subscriber is an actor, although the publisher can be any class.
Subscribers implement the EventSubscriber trait.

Event Publisher
---------------

The EventService class provides the method `publish(event)` to publish an object of type Event (EventServiceEvent).

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

The following should replace the config file at:

    hornetq-2.4.0.Final/config/stand-alone/non-clustered/hornetq-configuration.xml
    
```xml
<configuration xmlns="urn:hornetq"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="urn:hornetq /schema/hornetq-configuration.xsd">

   <paging-directory>${data.dir:../data}/paging</paging-directory>
   
   <bindings-directory>${data.dir:../data}/bindings</bindings-directory>
   
   <journal-directory>${data.dir:../data}/journal</journal-directory>
   
   <journal-min-files>10</journal-min-files>
   
   <large-messages-directory>${data.dir:../data}/large-messages</large-messages-directory>
   
   <connectors>
      <connector name="netty">
         <factory-class>org.hornetq.core.remoting.impl.netty.NettyConnectorFactory</factory-class>
         <param key="host"  value="${hornetq.remoting.netty.host:localhost}"/>
         <param key="port"  value="${hornetq.remoting.netty.port:5445}"/>
      </connector>
      
      <connector name="netty-throughput">
         <factory-class>org.hornetq.core.remoting.impl.netty.NettyConnectorFactory</factory-class>
         <param key="host"  value="${hornetq.remoting.netty.host:localhost}"/>
         <param key="port"  value="${hornetq.remoting.netty.batch.port:5455}"/>
         <param key="batch-delay" value="50"/>
      </connector>
   </connectors>

   <acceptors>
      <acceptor name="netty">
         <factory-class>org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory</factory-class>
         <param key="host"  value="${hornetq.remoting.netty.host:localhost}"/>
         <param key="port"  value="${hornetq.remoting.netty.port:5445}"/>
      </acceptor>
      
      <acceptor name="netty-throughput">
         <factory-class>org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory</factory-class>
         <param key="host"  value="${hornetq.remoting.netty.host:localhost}"/>
         <param key="port"  value="${hornetq.remoting.netty.batch.port:5455}"/>
         <param key="batch-delay" value="50"/>
         <param key="direct-deliver" value="false"/>
      </acceptor>
   </acceptors>

   <security-settings>
      <security-setting match="#">
         <permission type="createNonDurableQueue" roles="guest"/>
         <permission type="deleteNonDurableQueue" roles="guest"/>
         <permission type="consume" roles="guest"/>
         <permission type="send" roles="guest"/>
      </security-setting>
   </security-settings>

   <address-settings>
      <!--default for catch all-->
      <address-setting match="#">
         <dead-letter-address>jms.queue.DLQ</dead-letter-address>
         <redelivery-delay>0</redelivery-delay>
         <message-counter-history-day-limit>10</message-counter-history-day-limit>

         <max-size-bytes>104857600</max-size-bytes>
         <page-size-bytes>10485760</page-size-bytes>
         <address-full-policy>PAGE</address-full-policy>

      </address-setting>
   </address-settings>

</configuration>
```

By default Hornetq is configured to put messages in memory and block if there queue gets full.
The config file tells it to PAGE to the hornetq-2.4.0.Final/data directory instead of blocking.

You can run the test with:

  sbt "project event" test

