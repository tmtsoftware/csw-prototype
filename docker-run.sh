#!/usr/bin/env bash

# Starts Redis (Alarm Service, Event Service) and csw (Config Service) inside a docker container

# Get the IP address (Use boot2docker for Mac. Linux it should be the same IP?)
if  [ `which boot2docker` ] ; then
    eval "$(boot2docker shellinit)"
    host=`boot2docker ip`
else
    # set host to the current ip
    host=`ip route get 8.8.8.8 | awk '{print $NF; exit}'`
fi

# Start Redis (XXX changed to get a random free port!)
local_port=63799
docker run -t -d -p ${local_port}:6379 --name redis redis || exit 1

# Start the application
docker run -d -P -p 9000:9000 --name csw -Dcsw.redis.hostname=$host -Dcsw.redis.port=$local_port  || exit 1

# Note: For boot2docker, need to run this once the application is running to expose ports
# VBoxManage controlvm "boot2docker-vm" natpf1 "tcp-port9000,tcp,,9000,,9000";
