Configuration Service
=====================

This module implements the Configuration Service, which is used to manage configuration
files by storing them in a Git repository.

Main Packages:
--------------

* core - the core implementation of the API based on JGit
* akka - the Akka actor interface (based on core)

Large/binary files can slow down the Git repository, so these are stored separately using
the the [ConfigServiceAnnex](../apps/configServiceAnnex/README.md) http file server.

When you first create a config file, you can choose to store it in the normal way (in the Git repository)
or as a *large/binary* file, in which case only *$file.sha1* is checked in, containing the SHA-1 hash of the
file's contents. The actual binary file is then stored on the annex server in a file name based on the SHA-1 hash.

The config service also supports the concept of *default versions* of files. In this case a file named
*$file.default* is checked in to Git behind the scenes and contains the id of the default version of the file.
If there is no default, this file is not present and the latest version is always considered the default.

The config service can be started as a standalone application. *sbt stage* installs the command under
target/universal/stage/bin.
The standalone configs service registers itself with the [location service](../loc/README.md) so that it
can be found by other applications.

The contents of the files are exchanged using [Akka reactive streams](http://www.typesafe.com/activator/template/akka-stream-scala).

No concurrent access to local Git repository
--------------------------------------------

Note that each instance of the config service should manage its own local Git repository.
All of the local repositories may reference a common central repository, however it is not
safe to allow concurrent access to the local Git repository, which reads and writes files
in the Git working directory. The config service actor also enforces one-at-a-time processing
of commands acting on the local repository to ensure that reading and writing of the same files
does not conflict.

Having copies of the files in the Git working directory has the advantage of being a kind of *cache*,
so that the files do not have to be pulled from the server each time they are needed.
However care needs to be taken not to allow different threads to potentially read and write the
same files at once.

Running the tests
-----------------

To run the unit tests, use `sbt test`.
To run the multi-jvm tests, use `sbt multi-jvm:test`.




