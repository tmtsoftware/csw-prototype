package csw.services

/**
 * == Configuration Service ==
 *
 * This project implements the Configuration Service, which is used to manage configuration
 * files by storing them in a Git or Subversion repository.
 *
 * === Config Service API ===
 *
 * The config service can be accessed by sending messages to the config service actor,
 * however the ConfigServiceClient wrapper class implements the ConfigManager trait
 * and provides a somewhat simpler API.
 *
 * === Config Service Application ===
 *
 * Before starting the config service, the ''location service''
 * and the the ''config service annex server''
 * should both be running.
 * You can start the config service with the `cs` command (found under target/universal/stage/bin).
 * The default config service name and the locations of the repositories are defined in resources/reference.conf.
 * Alternatively you can specify a different config file on the command line in the same format.
 * You can also override the values with system properties. For example:
 *
 * {{{
 *      cs -Dcsw.services.cs.name=MyConfigServiceName
 *         -Dcsw.services.cs.main-repository=http://myHost/MyMainRepo/
 *         -Dcsw.services.cs.local-repository=/myPath/MyLocalRepo
 * }}}
 *
 * Note that multiple config service instances may be running in the network, but the names an host:port combinations should
 * each be unique. Only a single config service instance should access a given local repository.
 *
 * === Config Service Http Server ===
 *
 * The config service application (cs) also starts an http server
 * (optionally, if csw.services.cs.http.host is defined in the config file).
 * The HTTP/REST interface to the command service follows the scala and java APIs.
 *
 * The `create` and `update` methods expect the file data to be posted.
 * The `path` query argument is always required. All other query arguments are optional.
 * The `id` argument (a `ConfigId`) must be taken from the JSON result of `create`, `update`, `list`, or `history`.
 *
 * The format of the JSON returned from `create` and `update` is:
 * `{"ConfigId":"da807342bcc21766316c3a91a01f4a513a1adbb3"}`. The `id` query argument passed to the other methods is
 * the value at right.
 *
 * === Example or using curl to access the Config Service Http Server ===
 *
 * Assuming that the config service http server is running on localhost on port 8541 (see config file, default: reference.conf):
 *
 * {{{curl -X POST 'http://localhost:8541/create?path=some/test1/TestConfig1&comment=comment+here' --data-binary @TestConfig1}}}
 *
 * Creates a new file in the config service named some/test1/TestConfig1 using the data in the local file TestConfig1.
 *
 * {{{curl 'http://localhost:8541/get?path=some/test1/TestConfig1' > TestConfig1a}}}
 *
 * Gets the contents of some/test1/TestConfig1 from the service and store in a local file.
 *
 * {{{curl -X POST 'http://localhost:8541/update?path=some/test1/TestConfig1&comment=some+comment' --data-binary @TestConfig1}}}
 *
 * Updates the contents of some/test1/TestConfig1 in the config service with the contents of the local file.
 *
 * {{{curl -s 'http://localhost:8541/history?path=some/test1/TestConfig1'}}}
 *
 * Returns JSON describing the history of some/test1/TestConfig1. You can pipe the output to json_pp to pretty print it:
 *
 * {{{
 *     [
 *        {
 *           "comment" : "update 2 comment",
 *           "time" : 1421010506000,
 *           "id" : "3007e3369de4c05d4fb85d515df0be417243ecca"
 *        },
 *        {
 *           "comment" : "update 1 comment",
 *           "time" : 1421010505000,
 *           "id" : "0fff71d0f3f4f88aa5986aa02c32d3c495c1c652"
 *        },
 *        {
 *           "comment" : "create comment",
 *           "time" : 1421010505000,
 *           "id" : "f6e5266afc159f5d870ea1ac48c048ffa3913434"
 *        }
 *     ]
 *
 * }}}
 *
 * {{{curl 'http://localhost:8541/list'}}}
 *
 * Returns JSON listing the files in the config service repository.
 *
 * {{{
 *     [
 *        {
 *           "comment" : "create comment",
 *           "id" : "f6e5266afc159f5d870ea1ac48c048ffa3913434",
 *           "path" : "some/test2/TestConfig2"
 *        },
 *        {
 *           "comment" : "update 2 comment",
 *           "id" : "3007e3369de4c05d4fb85d515df0be417243ecca",
 *           "path" : "some/test1/TestConfig1"
 *        },
 *        {
 *           "comment" : "",
 *           "id" : "77c35d529c1e46113bd2f68b6f0550e81d8dbfec",
 *           "path" : "README"
 *        }
 *     ]
 *
 * }}}
 *
 * {{{curl 'http://localhost:8541/getDefault?path=some/test1/TestConfig1}}}
 *
 * Returns the content of the default version of the file, which may or may not be the same as the latest version (see below).
 *
 * {{{curl -X POST 'http://localhost:8541/setDefault?path=some/test1/TestConfig1&id=da807342bcc21766316c3a91a01f4a513a1adbb3'}}}
 *
 * Sets the default version of the file to the one with the given id (an id returned by the history command).
 *
 * {{{curl -X POST 'http://localhost:8541/resetDefault?path=some/test1/TestConfig1'}}}
 *
 * Resets the default version of the file to be the latest version.
 *
 *
 * === Main Packages ===
 *
 * - core - the core implementation of the API (based on Git or Svn)
 * - akka - (based on core) the Akka actor interface as well as the http server and client interfaces.
 *
 * Large/binary files can slow down the repository, so these are stored separately using
 * the the ConfigServiceAnnex http file server.
 *
 * When you first create a config file, you can choose to store it in the normal way (in the repository)
 * or as a *large/binary* file, in which case only *\$file.sha1* is checked in, containing the SHA-1 hash of the
 * file's contents. The actual binary file is then stored on the annex server in a file name based on the SHA-1 hash.
 *
 * The config service also supports the concept of *default versions* of files. In this case a file named
 * *\$file.default* is checked in behind the scenes and contains the id of the default version of the file.
 * If there is no default, this file is not present and the latest version is always considered the default.
 *
 * The config service can be started as a standalone application. *sbt stage* installs the command under
 * target/universal/stage/bin.
 * The standalone configs service registers itself with the location service so that it
 * can be found by other applications.
 *
 * The contents of the files are exchanged using [Akka reactive streams](http://www.typesafe.com/activator/template/akka-stream-scala).
 *
 * === No concurrent access to local repository ===
 *
 * Note that each instance of the config service actor should manage its own local repository, in order to ensure that
 * there are no conflicting, concurrent activities in the repository.
 *
 * === Running the tests ===
 *
 * To run the unit tests, use `sbt test`.
 * To run the multi-jvm tests, use `sbt multi-jvm:test`.
 */
package object cs {
}
