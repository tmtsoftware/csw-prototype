name := "org.tmt.csw.cmd.core"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    "com.typesafe" % "config" % "1.0.1",
    "net.liftweb" %% "lift-json" % "2.5" % "test",
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test"
)


//    "org.json4s" %% "json4s-native" % "3.2.4"
