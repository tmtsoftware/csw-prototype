#!/usr/bin/env bash

# Starts the docker image for the config service (cs)
#
# Note: If you want to run this script on a Mac: The Config Service uses the Location Service,
# which is based on Multicast DNS to advertise it's location. That doesn't work well with VirtualBox on
# the Mac, which docker uses. Not sure what the right network settings would be for that.

# user name for docker (Change this to your docker user name if you want to push to docker hub)
user=$USER

# Host directory to use to store repo data (default: if empty, keep in docker container)
#dataDir=/tmp/data

# add the -v option if dataDir is defined (make sure it exists)
if test ! -z $dataDir; then
    test -d $dataDir || mkdir -p $dataDir
    dataOpt=" -v $dataDir:/var/data"
fi

# Start the application
docker run --net host -t -P $dataOpt --name cs $user/cs || exit 1

