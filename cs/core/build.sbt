name := "org.tmt.csw.config.core"

organization := "org.tmt"

version := "1.0"

scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
    "org.eclipse.jgit" % "org.eclipse.jgit" % "2.3.1.201302201838-r",
    "org.gitective" % "gitective-core" % "0.9.9",
    "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
    "org.osgi" % "org.osgi.core" % "4.3.0" % "provided",
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test"
)

osgiSettings

OsgiKeys.exportPackage := Seq("org.tmt.csw.config")

// OsgiKeys.bundleActivator := Option("org.tmt.csw.config.core.osgi.Activator")
