#!/bin/sh
#
# Builds a docker image for the config service (cs).
#
# Note: This script tries to run in the docker environment, using the boot2docker shell, if found (on Mac OS).
# To start again from scratch, run these commands in a bash shell (assumes boot2docker is installed):
#
# boot2docker delete
# boot2docker init
# boot2docker up

# user name for docker (Change this to your docker user name if you want to push to docker hub)
user=$USER

# version tag
version=latest

# Get the IP address (Use boot2docker for Mac. Linux it should be the same IP?)
if  [ `which boot2docker` ] ; then
    eval "$(boot2docker shellinit)"
fi

cd ..
sbt "project cs" docker:stage || exit 1
cd cs/target/docker/stage
docker build -t $user/cs:$version .  || exit 1

# Push to docker hub...
# docker push $user/cs:latest
