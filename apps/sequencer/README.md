Sequencer
=========

This project implements the command line sequencer application, which is a Scala REPL shell
with the environment set up to enable working with configs, HCDs and assemblies, either
interactively or in Scala scripts.

This app assumes that the location service, HCDs and assemblies are already running in remote JVMs.
You can get the actorRef of an HCD or assembly from the location service and submit configs to it 
or send and receive other supported messages.

Building
--------

Type `sbt stage` to generate the sequencer script (under target/universal/stage/bin).

Running
-------

After starting the location service and any HCDs or assemblies you want to test, run the `sequencer` script.
Any command line arguments are interpreted as Scala scripts to execute.
The classpath and imports are automatically set up to be able to work with the command service.
You can type in scala code interactively or load scala scripts using the `:load` command.
See the [Scala documentation](http://docs.scala-lang.org/scala/2.11/) for more on the other commands available.

Example Session
---------------

After initializing the environment, it presents you with the `seq>` prompt, where you can enter Scala code.
The [csw.services.apps.sequencer.Seq](src/main/scala/csw/services/apps/sequencer/Seq.scala) class provides 
some convenient utility methods for use in the shell to get started.
The initialization code (see [resources/Init.scala](src/main/resources/Init.scala)) automatically imports 
the contents of the Seq object.

    -> sequencer
    Initializing...
    [INFO] [05/16/2014 20:57:39.068] [main] [Remoting] Starting remoting
    [INFO] [05/16/2014 20:57:39.276] [main] [Remoting] Remoting started; listening on addresses :[akka.tcp://Sequencer@127.0.0.1:57507]
    [INFO] [05/16/2014 20:57:39.278] [main] [Remoting] Remoting now listens on addresses: [akka.tcp://Sequencer@127.0.0.1:57507]
    Welcome to the TMT sequencer
    
    seq> val a1 = resolveAssembly("Assembly-1")
    a1: csw.services.cmd.akka.BlockingCommandServiceClient = BlockingCommandServiceClient(CommandServiceClient(Actor[akka://Sequencer/user/Assembly-1Client#-1698508007],5 seconds))
    
    seq> val conf = Configuration().withValue("config.tmt.mobie.blue.filter.value", "blue")
    conf: csw.util.Configuration = config{tmt{mobie{blue{filter{value=blue}}}}}
    
    seq> val s = a1.submit(conf)
    s: csw.services.cmd.akka.CommandStatus = Completed(4528d456-13cc-42dc-9f32-17dfbfaebc2f)
    
    seq> a1.getConfig(conf)
    res1: csw.util.Configuration = config{tmt{mobie{blue{filter{value=blue,timestamp=1400266708517}}}}}
    
    seq> a1.submit(conf.withValue("config.tmt.mobie.blue.filter.value", "red"))
    res2: csw.services.cmd.akka.CommandStatus = Completed(d9e1bde3-37ac-4812-b0fc-f5e41aefe451)
    
    seq> a1.getConfig(conf)
    res3: csw.util.Configuration = config{tmt{mobie{blue{filter{value=red,timestamp=1400266750419}}}}}

    // Container lifecycle commands: "stop" unitializes the container's components, restart starts does uninit followed
    // by a restart, halt causes the container to uninit and then exit.
    seq> val c2 = resolveContainer("Container-2")
    c2: csw.services.apps.sequencer.Seq.ContainerClient = ContainerClient(Actor[akka.tcp://Container-2-system@127.0.0.1:57192/user/Container-2#2046294920])
    seq> c2.stop
    seq> c2.restart
    seq> c2.halt


Ending the session
------------------

You should be able to end the session by enterrin `:quit` at the prompt. 
If that hangs, type Control-C (The actor threads my prevent exiting).

