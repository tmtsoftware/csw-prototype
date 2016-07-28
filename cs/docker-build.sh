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

# user name for docker push (Change this to your docker user name)
user=$USER

# version tag
version=latest

cd ..
sbt "project cs" docker:stage || exit 1
cd cs/target/docker/stage
docker build -t $user/cs:$version .  || exit 1

# Push to docker hub...
# docker push $user/cs:latest

# Note: For boot2docker, may need to run this once the application is running to expose ports
# VBoxManage controlvm "boot2docker-vm" natpf1 "tcp-port9000,tcp,,9000,,9000";
