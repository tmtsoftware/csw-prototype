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

