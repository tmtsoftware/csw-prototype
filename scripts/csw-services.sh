#!/bin/bash
#
# Starts serviced required by CSW, registered with the location service:
#    Event Service, Alarm Service, Telemetry Service
#
# Note that the Config Service can be started with the csw 'cs' command.

REDIS_SERVER=/usr/local/bin/redis-server
REDIS_CLIENT=/usr/local/bin/redis-cli

REDIS1_PORT=7777
#REDIS2_PORT=...

# Dir to hold pid and log files, svn repo
CSW_DATA_DIR=/tmp/csw
test -d $CSW_DATA_DIR || mkdir -p $CSW_DATA_DIR

REDIS1_PID_FILE=$CSW_DATA_DIR/start-csw-services.pid
LOG_FILE=$CSW_DATA_DIR/start-csw-services.log
OS=`uname`

# Additional pids for avahi or dns-sd processes
REG_PID_FILE=$CSW_DATA_DIR/pids

case "$1" in
    start)
        if [ -f $REDIS1_PID_FILE ] ; then
            echo "Redis $REDIS1_PID_FILE exists, process is already running or crashed"
        else
            if [ $OS != "Linux" -a "$OS" != "Darwin" ] ; then
                echo "This script only supports Linux and Mac OS"
                exit 1
            fi
            echo "Starting Redis server..."
            rm -f $REG_PID_FILE
            $REDIS_SERVER --port $REDIS1_PORT --protected-mode no --daemonize yes --pidfile $REDIS1_PID_FILE --logfile $LOG_FILE
            if [ "$OS" == "Linux" ] ; then
                avahi-publish -s "Event Service-Service-tcp" _csw._tcp $REDIS1_PORT >> $LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
                avahi-publish -s "Alarm Service-Service-tcp" _csw._tcp $REDIS1_PORT >> $LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
                avahi-publish -s "Telemetry Service-Service-tcp" _csw._tcp $REDIS1_PORT >> $LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
            else
                dns-sd -R "Event Service-Service-tcp" _csw._tcp local. $REDIS1_PORT >> $LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
                dns-sd -R "Alarm Service-Service-tcp" _csw._tcp local. $REDIS1_PORT >> $LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
                dns-sd -R "Telemetry Service-Service-tcp" _csw._tcp local. $REDIS1_PORT >> $LOG_FILE 2>&1 &
                echo $! >> $REG_PID_FILE
            fi
        fi
        ;;
    stop)
        if [ ! -f $REDIS1_PID_FILE ]
        then
            echo "Redis $REDIS1_PID_FILE does not exist, process is not running"
        else
            PID=$(cat $REDIS1_PID_FILE)
            echo "Stopping ..."
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
        fi
        ;;
    *)
        echo "Please use start or stop as first argument"
        ;;
esac
