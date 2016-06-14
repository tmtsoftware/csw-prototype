Track Location
==============

This project provides a standalone application that is a simple wrapper for an external application that
registers it with the location service and unregisters it when it exits.

```
Usage: trackLocation [options] [<app-config>]

  --name <name>
        Required: The name used to register the application (also root name in config file)
  --cs-config <value>
        optional config file to use for the Config Service (to enable fetching the application config file)
  -c <name> | --command <name>
        The command that starts the target application (default: use $name.command from config file: Required)
  -p <number> | --port <number>
        Port number the application listens on (default: use value of $name.port from config file. Required.)
  <app-config>
        optional config file in HOCON format (Options specified as: $name.command, $name.port, etc. Fetched from config service if path does not exist)
  --help
        prints this message
  --version
        prints the version of this application
 ```

Example Usage
-------------

One way to start and track a Redis instance is to use this command:

    tracklocation --name redisTest --command 'redis-server --port 7777' --port 7777

Or you can put the settings in a config file: redisTest.conf

```
redisTest {
  port = 7777
  command = redis-server --port 7777
}
```

And then run this command:

    tracklocation --name redisTest redisTest.conf

If the config file is stored in the Config Service under test/redisTest.conf, you can use this command:

    tracklocation --name redisTest test/redisTest.conf

If the path name is not found locally, it is searched for with the config service.
You can pass another config file to specify the config service to use. For example,
if the file cs.conf contains this:

```
csw.services.cs {

  // Name of this config service
  name = "Test Config Service"

  // The URI of the main git or svn repository used by the Config Service.
  main-repository = "file:///tmp/CsTestMainRepo/"

  // If this section is missing, the config service http server will not be started
  http {
    // Host to listen to for config service http server (can also use "0.0.0.0")
    interface = localhost

    // Port to listen on for config service http server (use 0 to get a random port assigned)
    port = 8542
  }

  // Timeout for ask messages
  timeout = 5000 milliseconds
}

```

