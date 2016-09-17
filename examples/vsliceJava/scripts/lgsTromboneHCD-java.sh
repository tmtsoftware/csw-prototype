#!/bin/sh
exec scala "$0" "$@"
!#

// Demonstrates starting a container with the java version of the lgsTrombone HCD using config files retrieved from the config service,
// a file passed on the command line, or a resource file.
// This script should be run from this dir (csw/install/bin)
// and assumes that it is in the shell path.
// (Note: "ls".run runs ls in the background, while "ls".! runs ls and waits. )
// (Note: Requires that the necessary dependencies are available, which currently means the csw source was built on
//  this host)

import scala.sys.process._

// config file describing the name and location of the config service repo
val config = "../../csw/cs/src/test/resources/test.conf"

// Start the config service, creating temporary main and local repositories (TODO: add -config option)
// (The -delete and -init options tell it to delete and create the local and main Git repos, so we start with a clean repo)
s"cs --delete --config $config".run

// Create the container config files in the config service
s"csclient create vslice/lgsTromboneHCD.conf --config $config -i ../../csw/examples/vsliceJava/src/main/resources/lgsTromboneHCD.conf".!

// Since the files are not found locally, they will be fetched from the config service
s"vslicejava hcd vslice/lgsTromboneHCD.conf --config $config".run

