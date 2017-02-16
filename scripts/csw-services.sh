#!/bin/bash
#
# Starts serviced required by CSW and registers them with the location service.
# This script uses the csw trackLocation app to start Redis and register it with the
# Location Service under different names (see below).
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

redisServer=/usr/local/bin/redis-server

# We need at least this version of Redis
minRedisVersion=3.2.5

# Look in the default location first, since installing from the source puts it there, otherwise look in the path
if test ! -x $redisServer ; then
    redisServer=redis-server
fi
if ! type $redisServer &> /dev/null; then
  echo "Can't find $redisServer. Please install Redis version $minRedisVersion or greater"
  exit 1
fi

sortVersion="sort -V"
test `uname` == Darwin && sortVersion="sort"

# Make sure we have the min redis version
function version_gt() { test "$(printf '%s\n' "$@" | $sortVersion | head -n 1)" != "$1"; }
redis_version=`$redisServer --version | awk '{sub(/-.*/,"",$3);print $3}' | sed -e 's/v=//'`
if version_gt $minRedisVersion $redis_version; then
     echo "Error: required Redis version is $minRedisVersion, but only version $redis_version was found"
     exit 1
fi
redisClient=`echo $redisServer | sed -e 's/-server/-cli/'`

# Set to yes to start the config service
startConfigService=yes

# Gets a random, unused port and sets the RANDOM_PORT variable
function random_unused_port {
    local port=$(shuf -i 2000-65000 -n 1)
    netstat -lat | grep $port > /dev/null
    if [[ $? == 1 ]] ; then
        export RANDOM_PORT=$port
    else
        random_unused_port
    fi
}

redisServices="Event Service,Alarm Service,Telemetry Service"

# Dir to hold pid and log files, svn repo (CSW_SERVICE_PREFIX is used if set to make the dir unique - see csw/loc/README.md)
cswDataDir=/tmp/csw$CSW_SERVICE_PREFIX
test -d $cswDataDir || mkdir -p $cswDataDir

# Config Service pid and log files
csPidFile=$cswDataDir/cs.pid
csLogFile=$cswDataDir/cs.log
# Config Service options
csOptions="--init --nohttp --noannex"

# Redis pid and log files
redisPidFile=$cswDataDir/redis.pid
redisLogFile=$cswDataDir/redis.log
redisPortFile=$cswDataDir/redis.port

# Required to be installed in $CSW_INSTALL
cswRequired="bin/cs bin/tracklocation bin/asconsole conf/alarms.conf"

case "$1" in
    start)

        # Start the Config Service
        if [ ! -d "$CSW_INSTALL" ] ; then
            echo "Please set CSW_INSTALL to the root directory where the csw software is installed"
            exit 1
        else
            for i in $cswRequired; do
                if [ ! -f $CSW_INSTALL/$i ] ; then
                    echo "Missing required file under \$CSW_INSTALL: $i"
                    exit 1
                fi
            done
            # Start Config Service
            if [ "$startConfigService" == "yes" ] ; then
                if [ -f $csPidFile ] ; then
                    echo "Config Service pid file $csPidFile exists, process is already running or crashed?"
                else
                    $CSW_INSTALL/bin/cs $csOptions > $csLogFile 2>&1 &
                    echo $! > $csPidFile
                fi
            fi

            # Start Redis based services using trackLocation
            if [ -f $redisPidFile ] ; then
                echo "Redis pid file $redisPidFile exists, process is already running or crashed?"
            else
               random_unused_port
               $CSW_INSTALL/bin/tracklocation --name "$redisServices" --port $RANDOM_PORT --command "$redisServer --protected-mode no --notify-keyspace-events KEA --port $RANDOM_PORT" > $redisLogFile 2>&1 &
                echo $! > $redisPidFile
                echo $RANDOM_PORT > $redisPortFile
				# Load the default alarms in to the Alarm Service Redis instance
				$CSW_INSTALL/bin/asconsole --init $CSW_INSTALL/conf/alarms.conf >> $redisLogFile 2>&1 &
			fi
        fi
        ;;
    stop)
        # Stop Redis
        if [ ! -f $redisPidFile ]
        then
            echo "Redis $redisPidFile does not exist, process is not running"
        else
            PID=$(cat $redisPidFile)
            redisPort=$(cat $redisPortFile)
            echo "Stopping Redis..."
            $redisClient -p $redisPort shutdown
            while [ -x /proc/${PID} ]
            do
                echo "Waiting for Redis to shutdown ..."
                sleep 1
            done
            echo "Redis stopped"
            rm -f $redisLogFile $redisPidFile $redisPortFile
        fi
        # Stop Config Service
        if [ "$startConfigService" == "yes" ] ; then
            if [ ! -f $csPidFile ]; then
				echo "Config Service $csPidFile does not exist, process is not running"
            else
				PID=$(cat $csPidFile)
				echo "Stopping Config Service..."
				kill $PID
				rm -f $csPidFile $csLogFile
            fi
        fi
        ;;
    *)
        echo "Please use start or stop as first argument"
        ;;
esac
