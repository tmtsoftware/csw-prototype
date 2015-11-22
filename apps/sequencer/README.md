Sequencer
=========

This project implements the command line sequencer application, which is a Scala REPL shell
with the environment set up to enable working with configs, HCDs and assemblies, either
interactively or in Scala scripts.

This app assumes that the HCDs and assemblies are already running in remote JVMs.
You can get the actorRef of an HCD or assembly from the location service and submit configs to it 
or send and receive other supported messages.

Building
--------

The top level install.sh script installs the `sequencer` command in the ../../install/bin directory.
Manually, you can type `sbt stage` to generate the sequencer script (under target/universal/stage/bin).

Running
-------

After starting any HCDs or assemblies you want to test, run the `sequencer` script.
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
    Welcome to the TMT sequencer
    
    // Get a reference to an assembly named Assembly-1 from the location service
    seq> val a1 = resolveAssembly("Assembly-1")
    a1: csw.services.ccs.BlockingAssemblyClient = BlockingAssemblyClient(AssemblyClient(Actor[akka.tcp://Assembly-1-system@192.168.2.2:61574/user/Assembly-1-supervisor#557963416]))
    
    // Create a configuration to submit to the assembly
    seq> val config = SetupConfig(filterPrefix).set(filter, "Y_G0323")
    config: csw.util.cfg.Configurations.SetupConfig = SC[TCS, tcs.mobie.blue.filter](filter -> Y_G0323)

    seq> val obsId = "obs001" // should be actual obsId
    obsId: String = obs001
    
    seq> val configArg = SetupConfigArg(obsId, config)
    configArg: csw.util.cfg.Configurations.SetupConfigArg = SetupConfigArg(ConfigInfo(ObsId(obs001)),WrappedArray(SC[TCS, tcs.mobie.blue.filter](filter -> Y_G0323)))

    // Submit the configuration to the assembly
    seq> val s = a1.submit(configArg)
    s: csw.services.ccs.CommandStatus = Completed(RunId(7044dd4c-33b2-4006-ad13-0d8d2a1e6d5a))
    
    // Get the current values from the assembly
    seq> a1.configGet(configArg)
    res3: csw.util.cfg.Configurations.SetupConfigArg = SetupConfigArg(ConfigInfo(ObsId(obs001)),ArrayBuffer(SC[TCS, tcs.mobie.blue.filter](filter -> Y_G0323)))
    
    // Set the filter to None
    seq> a1.submit(SetupConfigArg(obsId, config.set(filter, "None")))
    res5: csw.services.ccs.CommandStatus = Completed(RunId(3c35f05d-6fe0-4c4d-a8a2-434cdc5ac39b))

    // Check that the filter is now set to None
    seq> a1.configGet(configArg)
    res6: csw.util.cfg.Configurations.SetupConfigArg = SetupConfigArg(ConfigInfo(ObsId(obs001)),ArrayBuffer(SC[TCS, tcs.mobie.blue.filter](filter -> None)))

    // Get a reference to a container named Container-2-opc (from the csw-opc-demo)
    seq> val c2 = resolveContainer("Container-2-opc")
    c2: csw.services.apps.sequencer.Seq.ContainerClient = ContainerClient(Actor[akka.tcp://Container-2-opc-system@192.168.2.2:61575/user/Container-2-opc#-230882620])

    // Container lifecycle commands: "stop" unitializes the container's components, restart starts does uninit followed
    // by a restart, halt causes the container to uninit and then exit.
    seq> c2.stop

    // Submitting a config to the assembly times out now, since the target container is stopped
    seq> a1.submit(SetupConfigArg(obsId, config.set(filter, "Y_G0323")))
    java.util.concurrent.TimeoutException: Futures timed out after [60 seconds]

    seq> c2.restart

    // Now submits work again, since the container was restarted
    seq> a1.submit(SetupConfigArg(obsId, config.set(filter, "Y_G0323")))
    res10: csw.services.ccs.CommandStatus = Completed(RunId(d6299aaa-7e59-4486-aa31-1e2cc228883f))

    // Shutdown the container
    seq> c2.halt
    
    // You can also execute scripts from the command line like this:
    :load ../../../../src/test/resources/testscript


Ending the session
------------------

You should be able to end the session by enterring `:quit` at the prompt. 
If that hangs, type Control-C (The actor threads my prevent exiting).

