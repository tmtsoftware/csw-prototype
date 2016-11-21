#!/bin/sh
#
# Like install.sh, but skips generating the docs

dir=../install

hash sbt 2>/dev/null || { echo >&2 "Please install sbt first.  Aborting."; exit 1; }

stage=target/universal/stage

for i in bin lib conf doc ; do
    test -d $dir/$i || mkdir -p $dir/$i
done

sbt publish-local stage

for i in bin lib ; do
    for j in */target/universal/stage/$i/* apps/*/target/universal/stage/$i/* examples/*/target/universal/stage/$i/* ; do
        cp -f $j $dir/$i
    done
done

cp examples/vslice/scripts/lgsTromboneHCD.sh $dir/bin
cp examples/vsliceJava/scripts/lgsTromboneHCD-java.sh $dir/bin

cp scripts/*.sh $dir/bin
chmod +x $dir/bin/*.sh

# XXX FIXME: Get the real alarms.conf file from somewhere
cp examples/vslice/src/test/resources/test-alarms.conf $dir/conf/alarms.conf

rm -f $dir/bin/*.log.* $dir/bin/*.bat

# create the scalas script, for scala scriping (see http://www.scala-sbt.org/release/docs/Scripts.html)
# Note: This depends on the sbt version declared in project/build.properties (0.13.8).
echo 'sbt -Dsbt.main.class=sbt.ScriptMain "$@"' > $dir/bin/scalas
chmod ugo+x $dir/bin/scalas
