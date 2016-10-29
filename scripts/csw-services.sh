#!/bin/bash
#
# Starts serviced required by CSW and registers them with the location service.
#
# Usage is:
#
#  csw-services.sh start     - to start redis and register it for the event, alarm and telemetry services
#  csw-services.sh stop      - to stop redis and unregister from the location service
#
# The services are registered as:
#   "Event Service"
#   "Telemetry Service"
#   "ALarm Service"
#   "Config Service"
#
# Note that the environment variable CSW_INSTALL must be defined to point to the root of the csw install dir
# (This is usually ../install relative to the csw sources and is created by the install.sh script).
#

REDIS_SERVER=/usr/local/bin/redis-server
REDIS_CLIENT=/usr/local/bin/redis-cli

REDIS1_PORT=7777
#REDIS2_PORT=...

OS=`uname`

# Dir to hold pid and log files, svn repo
CSW_DATA_DIR=/tmp/csw
test -d $CSW_DATA_DIR || mkdir -p $CSW_DATA_DIR

# Config Service pid and log files
CS_PID_FILE=$CSW_DATA_DIR/cs.pid
CS_LOG_FILE=$CSW_DATA_DIR/cs.log

# Redis pid and log files
REDIS1_PID_FILE=$CSW_DATA_DIR/redis1.pid
REDIS1_LOG_FILE=$CSW_DATA_DIR/redis1.log

# Log and pid file for additional pids for avahi or dns-sd processes
REG_PID_FILE=$CSW_DATA_DIR/reg.pids
REG_LOG_FILE=$CSW_DATA_DIR/reg.log

case "$1" in
    start)

        # Start the Config Service
        if [ ! -d "$CSW_INSTALL" ] ; then
            echo "Please set CSW_INSTALL to the root directory where the csw software is installed"
            exit 1
        else
            $CSW_INSTALL/bin/cs --init > $CS_LOG_FILE 2>&1 &
            echo $! > $CS_PID_FILE
        fi

	# Start Redis and register Redis based services
        if [ -f $REDIS1_PID_FILE ] ; then
            echo "Redis $REDIS1_PID_FILE exists, process is already running or crashed"
        else
            if [ $OS != "Linux" -a "$OS" != "Darwin" ] ; then
                echo "This script only supports Linux and Mac OS"
                exit 1
            fi
            echo "Starting Redis server on port $REDIS1_PORT..."
            rm -f $REG_PID_FILE $REG_LOG_FILE $REDIS1_LOG_FILE
            $REDIS_SERVER --port $REDIS1_PORT --protected-mode no --daemonize yes --pidfile $REDIS1_PID_FILE --logfile $REDIS1_LOG_FILE
            if [ "$OS" == "Linux" ] ; then
                avahi-publish -s "Event Service-Service-tcp" _csw._tcp $REDIS1_PORT >> $REG_LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
                avahi-publish -s "Alarm Service-Service-tcp" _csw._tcp $REDIS1_PORT >> $REG_LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
                avahi-publish -s "Telemetry Service-Service-tcp" _csw._tcp $REDIS1_PORT >> $REG_LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
            else
                dns-sd -R "Event Service-Service-tcp" _csw._tcp local. $REDIS1_PORT >> $REG_LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
                dns-sd -R "Alarm Service-Service-tcp" _csw._tcp local. $REDIS1_PORT >> $REG_LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
                dns-sd -R "Telemetry Service-Service-tcp" _csw._tcp local. $REDIS1_PORT >> $REG_LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
            fi

            # Load the default alarms in to the Alarm Service Redis instance
            $CSW_INSTALL/bin/asconsole --init $CSW_INSTALL/conf/alarms.conf
        fi
        ;;
    stop)
        # Stop Redis
        if [ ! -f $REDIS1_PID_FILE ]
        then
            echo "Redis $REDIS1_PID_FILE does not exist, process is not running"
        else
            PID=$(cat $REDIS1_PID_FILE)
            echo "Stopping Redis..."
            $REDIS_CLIENT -p $REDIS1_PORT shutdown
            while [ -x /proc/${PID} ]
            do
                echo "Waiting for Redis to shutdown ..."
                sleep 1
            done
            echo "Redis stopped"
            # Stop the avahi/dns-sd processes
            for pid in `cat $REG_PID_FILE`; do
                kill $pid
            done
            rm -f $REG_PID_FILE $REG_LOG_FILE $REDIS1_LOG_FILE $REDIS1_PID_FILE
        fi
        # Stop Config Service
        if [ ! -f $CS_PID_FILE ]
        then
            echo "Config Service $CS_PID_FILE does not exist, process is not running"
        else
            PID=$(cat $CS_PID_FILE)
            echo "Stopping Config Service..."
            kill $PID
            rm -f $CS_PID_FILE $CS_LOG_FILE
        fi
        ;;
    *)
        echo "Please use start or stop as first argument"
        ;;
esac
