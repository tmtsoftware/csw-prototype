#!/usr/bin/env bash

# Stops and removes the cs docker container and image
#
# Note: use: `docker stop cs` to stop the config service.
#       use: `docker start cs` to start it again

# user name for docker (Change this to your docker user name if you want to push to docker hub)
user=$USER

docker stop cs
docker rm cs
docker rmi cs
docker rmi $user/cs:latest
