# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]

### Changed

- Removed classes combining multiple configurations (SetupConfigArg, ObserveArg) and
  changed the API for assembly component actors to receive a Submit object with a 
  single Setup or Observe

- Name changes to avoid overloading the term `configuration`:
  The csw.util.config package has been renamed to csw.util.param (and javacsw.util.param).

  | Old Name  | New Name |
  | ------------- | ------------- |
  | Configurations  | Parameters  |
  | ConfigKey  | Prefix  |
  | ControlItemSet  | ControlCommand  |
  | SequenceItemSet  | SequencerCommand  |
  | ConfigData  | ParameterSet |
  | Item  | Parameter |
  | ConfigType  | ParameterSetType  |
  | ConfigFilters  | ParameterSetFilters  |
  | ConfigKeyType  | ParameterSetKeyData  |
  | item() | parameter()  |
  | sc()  | setup()   |
  | oc()  | observe()   |

* Setups and Observes now take a CommandInfo object as a first argument 

* New CommandList object for future use with sequencer

## [CSW v0.3-PDR] - 2016-12-03

### Added

- Added Java APIs and tests

- Added vslice and vsliceJava: detailed, vertical slice examples in Scala and Java

- Added AlarmService

- Added Scala and Java DSLs for working with configurations

- Added csw-services.sh startup script

### Changed

- Changed the APIs for HcdController, AssemblyController, Supervisor

- Changed APIs for working with configurations in Scala and Java

- Changed the Location Service APIs

- Updated all dependency versions, Akka version

- Changed APIs for Event and Telemetry Service


### Added
- Added [BlockingConfigManager](src/main/scala/csw/services/cs/core/BlockingConfigManager.scala) 
  (a blocking API to the Config Service)

- Added [PrefixedActorLogging](src/main/scala/csw/services/log/PrefixedActorLogging.scala) to use in place
  of ActorLogging, to include a component's subsystem and prefix in log messages (subsystem is part of a component's prefix)
  
- Added [HcdControllerClient](src/main/scala/csw/services/ccs/HcdControllerClient.scala) 
  and [AssemblyControllerClient](src/main/scala/csw/services/ccs/AssemblyControllerClient.scala) classes, as
  an alternative API that makes clear which methods can be call (or which messages can be sent to the actor)
  
- Add `get(path, date)` method to [ConfigManager](src/main/scala/csw/services/cs/core/ConfigManager.scala) and
  all Config Service APIs, so that you can get the version of a file for a given date

- Added new [Alarm Service](alarms) and [command line app](apps/asConsole).
  An Alarm Service [Java API](javacsw/README.alarms.md) is also available.

- Added a *Request* message to [AssemblyController](ccs/src/main/scala/csw/services/ccs/AssemblyController.scala) that
does something based on the contents of the configuration argument and returns a status and optional value (also a Setup).
The main difference between Request and Submit is that Request can return a value, while Submit only returns a status.

- Added Java APIs for most services (See the [javacsw](javacsw) and  [util](util) subprojects)

### Changed
- Renamed the earlier Hornetq based `event` project to [event_old](event_old) 
  and renamed the Redis based `kvs` project to [events](events). 
  Classes with *KeyValueStore* in the name have been renamed to use *EventService*. 

- Renamed Config Service Java interfaces to start with I instead of J, to be more like the other Java APIs

- Reimplemented parts of the configuration classes, adding Scala and Java DSLs (See [util](util))

- Changed most log messages to debug level, rather than info

- Reimplemented the configuration classes, adding type-safe APIs for Scala and Java, JSON I/O, serialization (See [util](util))

- Changes the install.sh script to generate Scala and Java docs in the ../install/doc/{java,scala} directories

- Changed the [Configuration Service](cs) to use svn internally by default instead of git. In the svn implementation there
  is only one repository, rather than a local and a main repository..

- Reimplemented the [Command and Control Service](ccs) and [component packaging](pkg) classes:
  New HcdController, AssemblyController traits.
  No longer using the Redis based StateVariableStore to post state changes:
  The new version inherits a PublisherActor trait. You can subscribe to state/status messages from HCDs and
  assemblies.

- Changed the design of the [Location Service](loc) APIs.

## [CSW v0.2-PDR] - 2015-11-19

