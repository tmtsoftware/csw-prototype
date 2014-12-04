ContainerCmd
============

This project implements a command line application that takes a config file
and starts a container with the given HCDs or assemblies.

Location Service must be running
--------------------------------

Note that before starting a container, the location service (loc) must be running.
It can be started by typing: ../../loc/target/universal/stage/bin/loc.

Build
-----

The build makes use of the sbt-native-packager plugin, which can produce standalone applications
and release packages, such as RPMs. To create the application for testing, run `sbt stage`.
A start script can then be found under `target/universal/stage/bin`.

Example
-------

For example, from the top level directory of this project (containerCmd), run:

```
    target/universal/stage/bin/containercmd src/test/resources/container1.conf
```

to start `Container-1`, which is described by this config file:

```
container {
    name = Container-1
    components {
        Assembly-1 {
            type = Assembly
            class = csw.pkgDemo.container1.Assembly1
            args = [Assembly-1]
        }
    }
}
```

In this case, a container named `Container-1` is created. Then an assembly named `Assembly-1`
is created using the given class and optional constructor arguments.

Now run this command:

```
    target/universal/stage/bin/containercmd src/test/resources/container2.conf
```

This creates `Container-2` from this config file:

```
container {
    name = "Container-2"
    components {
        HCD-2A {
            type = HCD
            class = csw.pkgDemo.container2.Hcd2
            path = config.tmt.mobie.blue.filter
            args = [HCD-2A, ${path}]
        }
        HCD-2B {
            type = HCD
            class = csw.pkgDemo.container2.Hcd2
            path = config.tmt.mobie.blue.disperser
            args = [HCD-2B, ${path}]
        }
        HCD-2C {
            type = HCD
            class = csw.pkgDemo.container2.Hcd2
            path = config.tmt.tel.base.pos
            args = [HCD-2C, ${path}]
        }
        HCD-2D {
            type = HCD
            class = csw.pkgDemo.container2.Hcd2
            path = config.tmt.tel.ao.pos.one
            args = [HCD-2D, ${path}]
        }
    }
}
```

and then creates the four HCDs using the given class and constructor arguments.

* The config file does not have to be a resource. The example files are just there for testing.

* The file can be in JSON format, in which case it should have the .json suffix,
  or in the typesafe config file format (see https://github.com/typesafehub/config).


