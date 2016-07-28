#!/usr/bin/env bash

# Starts the docker image for the config service (cs)

# user name for docker push (Change this to your docker user name)
user=$USER

if  [ `which boot2docker` ] ; then
    eval "$(boot2docker shellinit)"
    host=`boot2docker ip`
else
    # set host to the current ip
    host=`ip route get 8.8.8.8 | awk '{print $NF; exit}'`
fi

# Start the application
docker run --net host -t -P -p 9999:9999 --name cs $user/cs \
    -Djava.net.preferIPv4Stack=true \
    -Dcsw.services.cs.main-repository=file:///svnrepo/ \
    -Dcsw.services.cs.http.enabled=false \
    -Dakka.remote.netty.tcp.port=9999  \
    || exit 1


# Note: For boot2docker, might need to run this once the application is running to expose ports
# VBoxManage controlvm "boot2docker-vm" natpf1 "tcp-port9999,tcp,,9999,,9999";
