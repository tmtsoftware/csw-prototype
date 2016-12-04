Simple HCD Example
=======================

This project contains a very simple example HCD that works together with [../assemblyExample](../assemblyExample).

Running the HCD and Assembly
----------------------------

The csw/install.sh script installs the `assemblyexample` and `hcdexample` commands in ../install/bin.
These can be used to start the assembly and HCD. They use the location service API (based on Mulicast-DNS)
to connect.

Controlling the HCD
------------------------

The [../conf/assembly1script.txt](../conf/assembly1script.txt) file contains some Scala code to
make it easy to control the assembly via the command line.
Pass `assembly1script.txt` as an argument to the csw sequencer command, so you can set the `rate` interactively:

```
> sequencer assembly1script.txt
17:09:34.172 [main] INFO  LocationService - Using 192.168.178.38 as listening IP address
Initializing...
Reading assembly1script.txt ...
17:09:38.282 [Sequencer-akka.actor.default-dispatcher-4] INFO  LocationService - Using host = ...
Welcome to the TMT sequencer

seq> setHcdRate(3)
```


