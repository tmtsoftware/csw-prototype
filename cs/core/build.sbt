name := "org.tmt.csw.cs.core"

organization := "org.tmt"

version := "1.0"

scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
    "org.eclipse.jgit" % "org.eclipse.jgit" % "2.3.1.201302201838-r",
    "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
    "org.osgi" % "org.osgi.core" % "4.3.0" % "provided",
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test",
    "junit" % "junit" % "4.10"
)

//  "org.gitective" % "gitective-core" % "0.9.9",


osgiSettings

OsgiKeys.exportPackage := Seq(
    "org.tmt.csw.cs.core",
    "org.tmt.csw.cs.core.git"
)

// OsgiKeys.bundleActivator := Option("org.tmt.csw.cs.core.osgi.Activator")
