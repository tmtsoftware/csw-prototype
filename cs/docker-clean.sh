#!/usr/bin/env bash

# Stops and removes the cs docker container and image

# user name for docker push (Change this to your docker user name)
user=$USER

docker stop cs
docker rm cs
docker images
docker rmi cs
docker rmi $user/cs:latest
