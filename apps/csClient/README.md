CsClient
========

The `csclient` command is a command line client to the config service.

Usage
-----

This is the automatically generated usage message, which is printed if the
arguments are incorrect or missing:

```
Usage: scopt [get|create|update|list|history] <args>...

Command: get [options] <path>
gets file with given path from the config service and writes it to the output file
  <path>
        path name in Git repository
  -o <outputFile> | --out <outputFile>
        output file
  --id <value>
        optional version id of file to get

Command: create [options] <path>
creates the file with the given path in the config service by reading the input file
  <path>
        path name in Git repository
  -i <inputFile> | --in <inputFile>
        input file
  --oversize
        add this option for large/binary files
  -c <value> | --comment <value>
        optional create comment

Command: update [options] <path>
updates the file with the given path in the config service by reading the input file
  <path>
        path name in Git repository
  -i <inputFile> | --in <inputFile>
        input file
  -c <value> | --comment <value>
        optional create comment

Command: list
lists the files in the repository

Command: history <path>
shows the history of the given path
  <path>
        path name in Git repository
```
