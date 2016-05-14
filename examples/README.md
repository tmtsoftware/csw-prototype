Examples
===========

This directory contains some example code:

* assemblyExample - an example assembly that sends messages to the example HCD
 
* hcdExample - an example HCD that generates position events along with an event subscriber that logs them (for testing)

The conf directory contains some logstash config files to demonstrate logging to LogStash, ElasticSearch and Kibana.

Running the HCD and Assembly
----------------------------

The csw/install.sh script installs the `assemblyexample` and `hcdexample` commands in ../install/bin.
These can be used to start the assembly and HCD. They use the location service API (based on Mulicast-DNS)
to connect.

Controlling the Assembly
------------------------

The [examples/conf/assembly1script.txt](examples/conf/assembly1script.txt) file contains some Scala code to
make it easy to control the assembly via the command line.
Pass `assembly1script.txt` as an argument to the csw sequencer command, so you can set the `rate` interactively:

```
> sequencer assembly1script.txt
17:09:34.172 [main] INFO  LocationService - Using 192.168.178.38 as listening IP address
Initializing...
Reading assembly1script.txt ...
17:09:38.282 [Sequencer-akka.actor.default-dispatcher-4] INFO  LocationService - Using host = ...
Welcome to the TMT sequencer

seq> setRate(2)
```


Running Logstash, Elasticsearch and the Kibana Web UI
-----------------------------------------------------

Template config files for running Logstash on client and server machines are provided in the conf directory
(and need to be edited for the local environment, to correct the path names to the log files).
This is the basic setup, as described in "The Logstash Book":

![Logstash setup](http://michael.bouvy.net/blog/wp-content/uploads/2013/11/logstach-archi1.png)

On the central log server machine, run:

* redis-server
* elasticsearch --cluster.name=logstash
* logstash agent -f logstashCentral.conf

On the client machines, edit logstashShipper.conf to reference the server and then run:

* logstash agent -f logstashShipper.conf

Then go to http://localhost:9292/ to view the [Kibana](https://www.elastic.co/products/kibana) web UI (replace localhost with the name of the central log host).

For this demo you can select the az and el fields, which are extracted

The following diagram shows the relationships of the various applications in this demo:

![Log diagram](doc/logging.jpg)
