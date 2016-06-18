CsClient
========

The `csclient` command is a command line client to an already running config service.

Usage
-----

```
Usage: csclient [get|exists|create|update|createOrUpdate|list|history|setDefault|resetDefault|getDefault] [options] <args>...

  --cs-name <value>
        optional name of the config service to use (as registered with the location service)

Command: get [options] <path>
gets file with given path from the config service and writes it to the output file
  <path>
        path name in the repository
  -o <outputFile> | --out <outputFile>
        output file
  --id <value>
        optional version id of file to get

Command: exists <path>
checks if the path exists in the repository
  <path>
        path name in the repository

Command: create [options] <path>
creates the file with the given path in the config service by reading the input file
  <path>
        path name in the repository
  -i <inputFile> | --in <inputFile>
        input file
  --oversize
        add this option for large/binary files
  -c <value> | --comment <value>
        optional create comment

Command: update [options] <path>
updates the file with the given path in the config service by reading the input file
  <path>
        path name in the repository
  -i <inputFile> | --in <inputFile>
        input file
  -c <value> | --comment <value>
        optional create comment

Command: createOrUpdate [options] <path>
creates or updates the file with the given path in the config service by reading the input file
  <path>
        path name in the repository
  -i <inputFile> | --in <inputFile>
        input file
  --oversize
        add this option for large/binary files
  -c <value> | --comment <value>
        optional create comment

Command: list
lists the files in the repository
Command: history <path>
shows the history of the given path
  <path>
        path name in the repository

Command: setDefault [options] <path>
sets the default version of the file
  <path>
        path name in the repository
  --id <value>
        optional version id to set as default for file

Command: resetDefault <path>
resets the default to the latest version of the file
  <path>
        path name in the repository

Command: getDefault [options] <path>
gets the default version of the file
  <path>
        path name in the repository
  -o <outputFile> | --out <outputFile>
        output file

  --help

  --version

```

Example
-------

The command:

    csclient create test/container1.conf -i ../../csw-pkg-demo/container1/src/main/resources/container1.conf

creates the file `test/container1.conf` in the repository containing the contents of the path given with `-i`.

