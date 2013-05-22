name := "org.tmt.csw.cs.akka"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test",
    "org.tmt" %% "org.tmt.csw.cs.core" % "1.0",
    "org.osgi" % "org.osgi.core" % "4.3.0" % "provided"
)

osgiSettings

OsgiKeys.exportPackage := Seq(
    "org.tmt.csw.cs.akka"
)

OsgiKeys.importPackage := Seq(
    "org.tmt.csw.cs.api",
    "org.tmt.csw.cs.core"
)
