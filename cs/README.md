Configuration Service
=====================

This module implements the configuration service, which is used to manage configuration files by storing them in a Git repository.

Modules:
--------

* api - the basic API
* core - the core implementation of the API
* akka - the Akka actor interface (based on core)
* client - an easy to use layer over the Akka actor interface
