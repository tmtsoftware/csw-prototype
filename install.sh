#!/bin/sh
#
# Creates a single install directory from all the csw stage directories.

dir=../install

test -d $dir || mkdir -p $dir/{bin,lib,conf}
sbt publish-local stage
for i in bin lib ; do cp -f */target/universal/stage/$i/* $dir/$i/; done
rm -f $dir/bin/*.log.* $dir/bin/*.bat

# create the scalas script, for scala scriping (see http://www.scala-sbt.org/release/docs/Scripts.html)
sed < `which sbt` | sed -e 's/exec java /exec java -Dsbt.main.class=sbt.ScriptMain /' > $dir/bin/scalas
chmod ugo+x $dir/bin/scalas
