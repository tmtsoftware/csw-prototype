Config Service Annex
====================

An akka-http based service used by the Config Service to store and retrieve large/binary files
that should not be stored in the Git repository.

See [reference.conf](src/main/resources/reference.conf) for configuration options.
The files are stored in the configured directory using a file name and directory structure
based on the SHA-1 hash of the file contents (This is the same way Git stores data).
The file checked in to the Git repository is then named $file.sha1 and contains only
the SHA-1 hash value.

The server is based on akka-http, which uses reactive streams to manage the
flow of data between the client and server.


