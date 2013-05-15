name := "org.tmt.csw.cs.akka"

organization := "org.tmt"

version := "1.0"

scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.1.2",
    "com.typesafe.akka" %% "akka-testkit" % "2.1.2" % "test",
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test",
    "org.tmt" %% "org.tmt.csw.cs.core" % "1.0",
    "org.osgi" % "org.osgi.core" % "4.3.0" % "provided"
)

    //"org.eclipse.jgit" % "org.eclipse.jgit" % "2.3.1.201302201838-r",
    //"com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
    //"biz.aQute" % "bndlib" % "2.0.0.20130123-133441",
    //"junit" % "junit" % "4.10" % "test"


osgiSettings

OsgiKeys.exportPackage := Seq(
    "org.tmt.csw.cs.akka"
)

OsgiKeys.importPackage := Seq(
    "org.tmt.csw.cs.api",
    "org.tmt.csw.cs.core"
)
