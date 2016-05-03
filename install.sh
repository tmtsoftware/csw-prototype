#!/bin/sh
#
# Creates a single install directory from all the csw stage directories.

dir=../install
stage=target/universal/stage

test -d $dir || mkdir -p $dir/bin $dir/lib $dir/conf
sbt publish-local stage
#sbt unidoc genjavadoc:doc

for i in bin lib ; do
    for j in */target/universal/stage/$i/* apps/*/target/universal/stage/$i/* examples/*/target/universal/stage/$i/* ; do
        cp -f $j $dir/$i
    done
done

rm -f $dir/bin/*.log.* $dir/bin/*.bat

# create the scalas script, for scala scriping (see http://www.scala-sbt.org/release/docs/Scripts.html)
# Note: This depends on the sbt version declared in project/build.properties (0.13.8).
echo 'sbt -Dsbt.main.class=sbt.ScriptMain "$@"' > $dir/bin/scalas
chmod ugo+x $dir/bin/scalas
