#!/usr/bin/env bash

# Starts the docker image for the config service (cs)

# Port to use for Config Service
port=9999

# user name for docker (Change this to your docker user name if you want to push to docker hub)
user=$USER

# Start the application
docker run --net host -t -P --name cs $user/cs \
    || exit 1

# Note: For boot2docker, might need to run this once the application is running to expose ports
# VBoxManage controlvm "boot2docker-vm" natpf1 "tcp-port9999,tcp,,9999,,9999";
