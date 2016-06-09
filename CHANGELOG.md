# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]
### Added
- Added Java APIs for most services (See the [javacsw](javacsw) and  [util](util) subprojects)

### Changed
- Reimplemented the configuration classes, adding type-safe APIs for Scala and Java, JSON I/O, serialization (See [util](util))

- Changes the install.sh script to generate Scala and Java docs in the ../install/doc/{java,scala} directories

- Changed the configuration service to use svn internally by default instead of git. In the svn implementation there
  is only one repository, rather than a local and a main repository. See [cs](cs).

- Reimplemented the command service/pkg classes: New HcdController, AssemblyController traits.
  See [ccs](ccs) and [pkg](pkg). Nolonger using the Redis based StateVariableStore to post state changes:
  The new version inherits a PublisherActor trait. You can subscribe to state/status messages from HCDs and
  assemblies.

## [CSW v0.2-PDR] - 2015-11-19
