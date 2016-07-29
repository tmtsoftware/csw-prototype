#!/usr/bin/env bash

# Starts the docker image for the config service (cs)

# user name for docker (Change this to your docker user name if you want to push to docker hub)
user=$USER

# Start the application
docker run --net host -t -P --name cs $user/cs || exit 1

