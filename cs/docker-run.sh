#!/usr/bin/env bash

# Starts the docker image for the config service (cs)

# user name for docker (Change this to your docker user name if you want to push to docker hub)
user=$USER

# Get the IP address (Use boot2docker for Mac. Linux it should be the same IP?)
if  [ `which boot2docker` ] ; then
    eval "$(boot2docker shellinit)"
fi

# Start the application
docker run --net host -t -P --name cs $user/cs || exit 1

