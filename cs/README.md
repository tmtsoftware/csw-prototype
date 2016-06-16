Configuration Service
=====================

This module implements the Configuration Service, which is used to manage configuration
files by storing them in a repository (by default, using Subversion).

Config Service API
------------------

The config service can be accessed by sending messages to the config service actor,
however the ConfigServiceClient wrapper class implements a
[ConfigManager](src/main/scala/csw/services/cs/core/ConfigManager.scala) trait
and provides a somewhat simpler API.

The ConfigManager API is non-blocking (returns future values). If this is inconvenient,
you can always wrap the calls in Await.result(...). The Java API also contains a blocking API.

The data for the files being stored in the config service is passed as a
[ConfigData](src/main/scala/csw/services/cs/core/ConfigManager.scala) object,
which is based on Akka streams, but can also be accessed using files or strings.

Example using Scala asynchronous API with for comprehension:

```
  def runTests(manager: ConfigManager, oversize: Boolean)(implicit system: ActorSystem): Future[Unit] = {
    import system.dispatcher
    val result = for {

      // Add, then update the file twice
      createId1 ← manager.create(path1, ConfigData(contents1), oversize, comment1)
      createId2 ← manager.createOrUpdate(path2, ConfigData(contents1), oversize, comment1)
      updateId1 ← manager.update(path1, ConfigData(contents2), comment2)
      updateId2 ← manager.createOrUpdate(path1, ConfigData(contents3), oversize, comment3)

      // Check that we can access each version
      result1 ← manager.get(path1).flatMap(_.get.toFutureString)
      result2 ← manager.get(path1, Some(createId1)).flatMap(_.get.toFutureString)
      result3 ← manager.get(path1, Some(updateId1)).flatMap(_.get.toFutureString)
      result4 ← manager.get(path1, Some(updateId2)).flatMap(_.get.toFutureString)
      result5 ← manager.get(path2).flatMap(_.get.toFutureString)
      result6 ← manager.get(path2, Some(createId2)).flatMap(_.get.toFutureString)

      historyList1 ← manager.history(path1)
      historyList2 ← manager.history(path2)

      // Test default file features
      default1 ← manager.getDefault(path1).flatMap(_.get.toFutureString)
      _ ← manager.setDefault(path1, Some(updateId1))
      default2 ← manager.getDefault(path1).flatMap(_.get.toFutureString)
      _ ← manager.resetDefault(path1)
      default3 ← manager.getDefault(path1).flatMap(_.get.toFutureString)
      _ ← manager.setDefault(path1, Some(updateId2))

      // list the files being managed
      list ← manager.list()

    } yield {
      // At this point all of the above Futures have completed
      assert(result1 == contents3)
      ...
  }

```

Convenience Methods
-------------------

If there is a config service actor running already and you just want to get the latest version of
a config file (in HOCON format), there is a convenience method:

```
    val configOpt = Await.result(ConfigServiceClient.getConfigFromConfigService(csName, file), timeout.duration)
    if (configOpt.isEmpty) ...
```

This looks up the config service that was registered with the name `csName` in the location service and then
gets the contents of the given file, returning an Option[Config], if successful.


Config Service Application
--------------------------

Before starting the config service, the [config service annex server](../apps/configServiceAnnex)
should be running.
You can start the config service with the `cs` command (found under target/universal/stage/bin).
The default config service name and the location of the git or svn repository is defined in resources/reference.conf.
Alternatively you can specify a different config file on the command line in the same format.
You can also override the values with system properties. For example:

```
     cs -Dcsw.services.cs.name=MyConfigServiceName
        -Dcsw.services.cs.main-repository=http://myHost/MyMainRepo/
        -Dcsw.services.cs.local-repository=/myPath/MyLocalRepo
```

Note that multiple config service instances may be running in the network, but the names an host:port combinations should
each be unique. Only a single config service instance should access a given local repository.

Svn or Git
----------

There are currently two alternatives you can choose from for the version control system used by the config service.
You can configure this in the application settings by setting the value of `csw.services.cs.useSvn` to true to use Subversion or false to use Git.
(The default is to use svn, due to performance reasons - The access time stays constant as the history grows.)

Config Service Http Server
--------------------------

The config service application (cs) also starts an http server
(optionally, if csw.services.cs.http.host is defined in the config file).
The HTTP/REST interface to the command service follows the scala and java APIs:

| Method | Path    | Query Arguments                           | Response
---------|---------|-------------------------------------------|---------
| POST   | /create | path=_filePath_, comment=_create+comment_ | JSON with _id_ that can be used to reference this version of file
| GET    | /get    | path=_filePath_, id=_id_                  | Contents of file (current or version _id_)
| PUT    | /update | path=_filePath_, comment=_update+comment_ | JSON with _id_ that can be used to reference this version of file
| HEAD   | /exists | path=_filePath_                           | Status: OK if file exists, otherwise NotFound
| GET    | /list   |                                           | JSON list of available files
| GET    | /history      | path=_filePath_, maxResults=_count_ | JSON list of file history
| GET    | /getDefault   | path=_filePath_                     | Contents of _default_ version of file
| PUT    | /setDefault   | path=_filePath_, id=_id_            | Sets the _default_ version of the file
| PUT    | /resetDefault | path=_filePath_                     | Resets the _default_ version of the file to _current_

The _path_ query argument is always required. All other query arguments are optional.
The _id_ argument (a _ConfigId_) must be taken from the JSON result of _create_, _update_, _list_, or _history_.

The format of the JSON returned from _create_ and _update_ is:
`{"ConfigId":"da807342bcc21766316c3a91a01f4a513a1adbb3"}`. The _id_ query argument passed to the other methods is
 the value at right. (When using svn, the id is a simple number.)

Example or using curl to access the Config Service Http Server
--------------------------------------------------------------

Assuming that the config service http server is running on localhost on port 8541 (see config file, default: reference.conf):

`curl -X POST 'http://localhost:8541/create?path=some/test1/TestConfig1&comment=comment+here' --data-binary @TestConfig1`

   Creates a new file in the config service named some/test1/TestConfig1 using the data in the local file TestConfig1.

`curl 'http://localhost:8541/get?path=some/test1/TestConfig1' > TestConfig1a`

   Gets the contents of some/test1/TestConfig1 from the service and store in a local file.

`curl -X PUT 'http://localhost:8541/update?path=some/test1/TestConfig1&comment=some+comment' --data-binary @TestConfig1`

   Updates the contents of some/test1/TestConfig1 in the config service with the contents of the local file.

`curl -s 'http://localhost:8541/history?path=some/test1/TestConfig1'`

   Returns JSON describing the history of some/test1/TestConfig1. You can pipe the output to json_pp to pretty print it:

    ```
    [
       {
          "comment" : "update 2 comment",
          "time" : 1421010506000,
          "id" : "3007e3369de4c05d4fb85d515df0be417243ecca"
       },
       {
          "comment" : "update 1 comment",
          "time" : 1421010505000,
          "id" : "0fff71d0f3f4f88aa5986aa02c32d3c495c1c652"
       },
       {
          "comment" : "create comment",
          "time" : 1421010505000,
          "id" : "f6e5266afc159f5d870ea1ac48c048ffa3913434"
       }
    ]

    ```

`curl 'http://localhost:8541/list'`

   Returns JSON listing the files in the config service repository.

    ```
    [
       {
          "comment" : "create comment",
          "id" : "f6e5266afc159f5d870ea1ac48c048ffa3913434",
          "path" : "some/test2/TestConfig2"
       },
       {
          "comment" : "update 2 comment",
          "id" : "3007e3369de4c05d4fb85d515df0be417243ecca",
          "path" : "some/test1/TestConfig1"
       },
       {
          "comment" : "",
          "id" : "77c35d529c1e46113bd2f68b6f0550e81d8dbfec",
          "path" : "README"
       }
    ]

    ```

`curl 'http://localhost:8541/getDefault?path=some/test1/TestConfig1`

   Returns the content of the default version of the file, which may or may not be the same as the latest version (see below).

`curl -X PUT 'http://localhost:8541/setDefault?path=some/test1/TestConfig1&id=da807342bcc21766316c3a91a01f4a513a1adbb3'`

   Sets the default version of the file to the one with the given id (an id returned by the history command).

`curl -X PUT 'http://localhost:8541/resetDefault?path=some/test1/TestConfig1'`

   Resets the default version of the file to be the latest version.


Main Packages:
--------------

* core - the core implementation of the API
* akka - (based on core) the Akka actor interface as well as the http server and client interfaces.

Large/binary files can slow down the repository (especially when using Git), so these can be stored separately using
the the [ConfigServiceAnnex](../apps/configServiceAnnex) http file server. (Note: May not be needed when using svn.)

When you first create a config file, you can choose to store it in the normal way (in the repository)
or as a *large/binary* file, in which case only *$file.sha1* is checked in, containing the SHA-1 hash of the
file's contents. The actual binary file is then stored on the annex server in a file name based on the SHA-1 hash.

The config service also supports the concept of *default versions* of files. In this case a file named
*$file.default* is checked in behind the scenes and contains the id of the default version of the file.
If there is no default, this file is not present and the latest version is always considered the default.

The config service can be started as a standalone application. *sbt stage* installs the command under
target/universal/stage/bin.
The standalone configs service registers itself with the [location service](../loc) so that it
can be found by other applications.

The contents of the files are exchanged using [Akka reactive streams](http://www.typesafe.com/activator/template/akka-stream-scala).

No concurrent access to local repository
----------------------------------------

When using svn (the default setting) concurrent access should not be a problem, since no local repository or working
directory is used.

If using Git, each instance of the config service should manage its own local Git repository.
All of the local repositories may reference a common central repository, however it is probably not
safe to allow concurrent access by more than one config service actor to the local Git repository,
which reads and writes files in the Git working directory.

Running the tests
-----------------

To run the unit tests, use `sbt test`.
To run the multi-jvm tests, use `sbt multi-jvm:test`.


