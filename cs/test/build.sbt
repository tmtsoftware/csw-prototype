name := "org.tmt.csw.cs.test"

organization := "org.tmt"

version := "1.0"

scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
    "org.tmt" %% "org.tmt.csw.cs.api" % "1.0",
    "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
    "org.osgi" % "org.osgi.core" % "4.3.0" % "provided"
)

osgiSettings

//OsgiKeys.exportPackage := Seq(
//    "org.tmt.csw.cs.api"
//)

// OsgiKeys.bundleActivator := Option("org.tmt.csw.cs.core.osgi.Activator")
