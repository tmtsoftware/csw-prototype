/**
 * Defines synchronous (blocking) and asynchronous (non-blocking) Java APIs for the config service.
 * <p>
 * <strong>Configuration Service</strong>
 * <p>
 * This project implements the Configuration Service, which is used to manage configuration
 * files by storing them in a repository (by default, using Subversion).
 * <p>
 * <strong> Config Service API </strong>
 * <p>
 * The config service can be accessed by sending messages to the config service actor,
 * however client wrapper classes provide a somewhat simpler API.
 * See {@link javacsw.services.cs.JBlockingConfigManager} for a simple, blocking API
 * and {@link javacsw.services.cs.JConfigManager} for a non-blocking API that returns future values.
 * <p>
 * Data returned from the config service is accessed via the {@link javacsw.services.cs.JConfigData} interface,
 * which allows writing the data to a file or output stream or returning it as a String (assuming the data is not binary).
 * The interface also provides static factory methods to create instances to pass to the config service methods.
 * <p>
 *  Here is an example of how to use the Java blocking API to create, update and get files:
 * <pre> {@code
 *  // Lookup already running default config service with the location service
 *  JBlockingConfigServiceClient client = JConfigServiceFactory.getBlockingConfigServiceClient(system, timeout);
 *  Boolean oversize = false; // Set to true to for special handling of large files
 *
 *  // Add, then update the file twice
 *  ConfigId createId1 = manager.create(path1, JConfigData.create(contents1), oversize, comment1);
 *  ConfigId createId2 = manager.create(path2, JConfigData.create(contents1), oversize, comment1);
 *  ConfigId updateId1 = manager.update(path1, JConfigData.create(contents2), comment2);
 *  ConfigId updateId2 = manager.update(path1, JConfigData.create(contents3), comment3);
 *
 *  // Check that we can access each version
 *  JBlockingConfigData data1 = manager.get(path1).get();
 *  JBlockingConfigData data2 = manager.get(path1, createId1).get();
 *  JBlockingConfigData data3 = manager.get(path1, updateId1).get();
 *  JBlockingConfigData data4 = manager.get(path1, updateId2).get();
 *  JBlockingConfigData data5 = manager.get(path2).get();
 *  JBlockingConfigData data6 = manager.get(path2, createId2).get();
 *
 *  // Get the file history()
 *  List<ConfigFileHistory> historyList1 = manager.history(path1);
 *  List<ConfigFileHistory> historyList2 = manager.history(path2);
 *
 *  // Get a list of all the files in the config service
 *  List<ConfigFileInfo> list = manager.list();
 * }</pre>
 * <p>
 * <strong> Config Service Application </strong>
 * <p>
 * Before starting the config service, the ''config service annex server'' should  be running.
 * You can start the config service with the `cs` command (found under target/universal/stage/bin, or in the install/bin dir).
 * The default config service name and the location of the git or svn repository is defined in resources/reference.conf.
 * Alternatively you can specify a different config file on the command line in the same format.
 * You can also override the values with system properties. For example:
 * <pre> {@code
 * cs -Dcsw.services.cs.name=MyConfigServiceName
 * -Dcsw.services.cs.main-repository=http://myHost/MyMainRepo/
 * -Dcsw.services.cs.local-repository=/myPath/MyLocalRepo
 * }</pre>
 * Note that multiple config service instances may be running in the network, but the names an host:port combinations should
 * each be unique. Only a single config service instance should access a given local repository.
 * <p>
 * <strong> Config Service Http Server </strong>
 * <p>
 * The config service application (cs) also starts an http server
 * (optionally, if csw.services.cs.http.host is defined in the config file).
 * The HTTP/REST interface to the command service follows the scala and java APIs.
 * <p>
 * The `path` query argument is always required. All other query arguments are optional.
 * The `id` argument (a `ConfigId`) must be taken from the JSON result of `create`, `update`, `list`, or `history`.
 * <p>
 * The format of the JSON returned from `create` and `update` is:
 * `{"ConfigId":"da807342bcc21766316c3a91a01f4a513a1adbb3"}`. The `id` query argument passed to the other methods is
 * the value at right.
 * <p>
 * <strong> Example or using curl to access the Config Service Http Server </strong>
 * <p>
 * Assuming that the config service http server is running on localhost on port 8541 (see config file, default: reference.conf):
 * <pre> {@code
 * curl -X POST 'http://localhost:8541/create?path=some/test1/TestConfig1&comment=comment+here' --data-binary @TestConfig1
 * }</pre>
 * Creates a new file in the config service named some/test1/TestConfig1 using the data in the local file TestConfig1.
 * <pre> {@code
 * curl 'http://localhost:8541/get?path=some/test1/TestConfig1' > TestConfig1a
 * }</pre>
 * Gets the contents of some/test1/TestConfig1 from the service and store in a local file.
 * <pre> {@code
 * curl -X PUT 'http://localhost:8541/update?path=some/test1/TestConfig1&comment=some+comment' --data-binary @TestConfig1
 * }</pre>
 * Updates the contents of some/test1/TestConfig1 in the config service with the contents of the local file.
 * <pre> {@code
 * curl -s 'http://localhost:8541/history?path=some/test1/TestConfig1'
 * }</pre>
 * Returns JSON describing the history of some/test1/TestConfig1. You can pipe the output to json_pp to pretty print it:
 * <pre> {@code
 * [
 * {
 * "comment" : "update 2 comment",
 * "time" : 1421010506000,
 * "id" : "3007e3369de4c05d4fb85d515df0be417243ecca"
 * },
 * {
 * "comment" : "update 1 comment",
 * "time" : 1421010505000,
 * "id" : "0fff71d0f3f4f88aa5986aa02c32d3c495c1c652"
 * },
 * {
 * "comment" : "create comment",
 * "time" : 1421010505000,
 * "id" : "f6e5266afc159f5d870ea1ac48c048ffa3913434"
 * }
 * ]
 * }</pre>
 *
 * <pre> {@code
 * curl 'http://localhost:8541/list'
 * }</pre>
 * <p>
 * Returns JSON listing the files in the config service repository.
 * <p>
 * <pre> {@code
 * [
 * {
 * "comment" : "create comment",
 * "id" : "f6e5266afc159f5d870ea1ac48c048ffa3913434",
 * "path" : "some/test2/TestConfig2"
 * },
 * {
 * "comment" : "update 2 comment",
 * "id" : "3007e3369de4c05d4fb85d515df0be417243ecca",
 * "path" : "some/test1/TestConfig1"
 * },
 * {
 * "comment" : "",
 * "id" : "77c35d529c1e46113bd2f68b6f0550e81d8dbfec",
 * "path" : "README"
 * }
 * ]
 * }</pre>
 *
 * <pre> {@code
 * curl 'http://localhost:8541/getDefault?path=some/test1/TestConfig1
 * }</pre>
 * Returns the content of the default version of the file, which may or may not be the same as the latest version (see below).
 * <pre> {@code
 * curl -X POST 'http://localhost:8541/setDefault?path=some/test1/TestConfig1&id=da807342bcc21766316c3a91a01f4a513a1adbb3'
 * }</pre>
 * Sets the default version of the file to the one with the given id (an id returned by the history command).
 * <pre> {@code
 * curl -X POST 'http://localhost:8541/resetDefault?path=some/test1/TestConfig1'
 * }</pre>
 * Resets the default version of the file to be the latest version.
 * <p>
 * <strong> Main Packages </strong>
 * <p>
 * - core - the core implementation of the API based on git or svn
 * <p>
 * - akka - (based on core) the Akka actor interface as well as the http server and client interfaces.
 * <p>
 * Large/binary files can slow down the repository, so these can be stored separately using
 * the the ConfigServiceAnnex http file server.
 * <p>
 * When you first create a config file, you can choose to store it in the normal way (in the repository)
 * or as a <em>large/binary</em> file, in which case only <em>$file.sha1</em> is checked in, containing the SHA-1 hash of the
 * file's contents. The actual binary file is then stored on the annex server in a file name based on the SHA-1 hash.
 * <p>
 * The config service also supports the concept of *default versions* of files. In this case a file named
 * <em>$file.default</em> is checked in behind the scenes and contains the id of the default version of the file.
 * If there is no default, this file is not present and the latest version is always considered the default.
 * <p>
 * The config service can be started as a standalone application. <em>sbt stage</em> installs the command under
 * target/universal/stage/bin.
 * The standalone configs service registers itself with the location service so that it
 * can be found by other applications.
 * <p>
 * The contents of the files are exchanged using [Akka reactive streams](http://www.typesafe.com/activator/template/akka-stream-scala).
 * <p>
 * <strong> No concurrent access to local Git repository </strong>
 * <p>
 * Note that if using Git, each instance of the config service should manage its own local Git repository.
 * All of the local repositories may reference a common central repository, however it is probably not
 * safe to allow concurrent access to the local Git repository, which reads and writes files
 * in the Git working directory.
 * <p>
 * Having copies of the files in the Git working directory has the advantage of being a kind of <em>cache</em>,
 * so that the files do not have to be pulled from the server each time they are needed.
 * However care needs to be taken not to allow different threads to potentially read and write the
 * same files at once.
 * <p>
 * When using svn (the default setting) this should not be a problem, since no local repository or working
 * directory is used.
 * <p>
 * <strong> Running the tests </strong>
 * <p>
 * To run the unit tests, use <code>sbt test</code>.
 * <p>
 * To run the multi-jvm tests, use <code>sbt multi-jvm:test</code>.
 */
package javacsw.services.cs;