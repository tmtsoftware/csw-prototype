#!/bin/sh
#
# Builds a docker image for the config service (cs).

# user name for docker (Change this to your docker user name if you want to push to docker hub)
user=$USER

# version tag
version=latest

cd ..
sbt "project cs" docker:stage || exit 1
cd cs/target/docker/stage
docker build -t $user/cs:$version .  || exit 1

# Push to docker hub...
# docker push $user/cs:latest
