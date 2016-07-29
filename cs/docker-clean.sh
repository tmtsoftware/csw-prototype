#!/usr/bin/env bash

# Stops and removes the cs docker container and image
#
# Note: use: `docker stop cs` to stop the config service.
#       use: `docker start cs` to start it again

# user name for docker (Change this to your docker user name if you want to push to docker hub)
user=$USER

# Get the IP address (Use boot2docker for Mac. Linux it should be the same IP?)
if  [ `which boot2docker` ] ; then
    eval "$(boot2docker shellinit)"
fi

docker stop cs
docker rm cs
docker rmi cs
docker rmi $user/cs:latest
