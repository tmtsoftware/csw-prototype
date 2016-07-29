#!/usr/bin/env bash

# Starts the docker image for the config service (cs)
#
# Note: If you want to run this script on a Mac: The Config Service uses the Location Service,
# which is based on Multicast DNS to advertise it's location. That doesn't work well with VirtualBox on
# the Mac, which docker uses. Not sure what the right network settings would be for that.

# user name for docker (Change this to your docker user name if you want to push to docker hub)
user=$USER

# Start the application
docker run --net host -t -P --name cs $user/cs || exit 1

