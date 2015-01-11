Configuration Service
=====================

This module implements the Configuration Service, which is used to manage configuration
files by storing them in a Git repository.

Config Service Application
--------------------------

Before starting the config service, the [location service](../loc/README.md)
and the the [config service annex server](../apps/configServiceAnnex/README.md)
should both be running.
You can start the config service with the `cs` command (found under target/universal/stage/bin).
The default config service name and the location of the local and main Git repositories is defined in resources/reference.conf.
Alternatively you can specify a different config file on the command line in the same format.
You can also override the values with system properties. For example:

```
     cs -Dcsw.services.cs.name=MyConfigServiceName
        -Dcsw.services.cs.main-repository=http://myHost/MyMainRepo/
        -Dcsw.services.cs.local-repository=/myPath/MyLocalRepo
```

Note that multiple config service instances may be running in the network, but the names an host:port combinations should
each be unique. Only a single config service instance should access a given local repository.

Config Service Http Server
--------------------------

The config service application (cs) also starts an http server (on a port configured in the config file).
There is also a scala ConfigServiceHttpClient class that can be used to access the http server.

The HTTP/REST interface to the command service follows the scala and java APIs:

 Method| Path    | Query Arguments
--------------------------------------------
| POST | /create | path=_filePath_, comment=_create+comment_
| POST | /update | path=_filePath_, comment=_create+comment_


Example or using curl to access the Config Service Http Server
--------------------------------------------------------------

Assuming that the config service http server is running on localhost on port 8541 (see config file, default: reference.conf):

`curl -X POST 'http://localhost:8541/create?path=some/test1/TestConfig1&comment=comment+here' --data-binary @TestConfig1`

   Creates a new file in the config service named some/test1/TestConfig1 using the data in the local file TestConfig1.

`curl 'http://localhost:8541/get?path=some/test1/TestConfig1' > TestConfig1a`

    Gets the contents of some/test1/TestConfig1 from the service and store in a local file.

`curl -X POST 'http://localhost:8541/update?path=some/test1/TestConfig1&comment=some+comment' --data-binary @TestConfig1`

    Updates the contents of some/test1/TestConfig1 in the config service with the contents of the local file.

`curl 'http://localhost:8541/history?path=some/test1/TestConfig1'`

    Returns JSON describing the history of some/test1/TestConfig1.

`curl 'http://localhost:8541/list'`

    Returns JSON listing the files in the config service repository.

`curl 'http://localhost:8541/getDefault?path=some/test1/TestConfig1`

    Returns the content of the default version of the file, which may or may not be the same as the latest version (see below).

`curl -X POST 'http://localhost:8541/setDefault?path=some/test1/TestConfig1&id=da807342bcc21766316c3a91a01f4a513a1adbb3'`

    Sets the default version of the file to the one with the given id (an id returned by the history command).

`curl -X POST 'http://localhost:8541/resetDefault?path=some/test1/TestConfig1'`

    Resets the default version of the file to be the latest version.


Main Packages:
--------------

* core - the core implementation of the API based on JGit
* akka - (based on core) the Akka actor interface as well as the http server and client interfaces.

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


