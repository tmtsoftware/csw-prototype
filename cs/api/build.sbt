name := "org.tmt.csw.cs.api"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
    "org.osgi" % "org.osgi.core" % "4.3.0" % "provided"
)

osgiSettings

OsgiKeys.exportPackage := Seq(
    "org.tmt.csw.cs.api"
)
