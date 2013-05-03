name := "org.tmt.csw.cs.core"

organization := "org.tmt"

version := "1.0"

scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
    "org.tmt" %% "org.tmt.csw.cs.api" % "1.0",
    "org.eclipse.jgit" % "org.eclipse.jgit" % "2.3.1.201302201838-r",
    "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
    "org.osgi" % "org.osgi.core" % "4.3.0" % "provided",
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test",
    "junit" % "junit" % "4.10"
)

osgiSettings

OsgiKeys.exportPackage := Seq(
    "org.tmt.csw.cs.core",
    "org.tmt.csw.cs.core.git"
)

// Configure the locations of the local and remote Git repos used here
OsgiKeys.additionalHeaders := Map(
  "org.tmt.csw.cs.core.remoteRepo" -> "git@localhost:project.git",
  "org.tmt.csw.cs.core.gitWorkDir" -> "~/.csw/cs"
)

OsgiKeys.bundleActivator := Option("org.tmt.csw.cs.core.osgi.Activator")
