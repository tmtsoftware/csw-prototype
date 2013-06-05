name := "org.tmt.csw.cmd.akka"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "org.json4s" %% "json4s-native" % "3.2.4",
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test"
)

    //"org.tmt" %% "org.tmt.csw.cmd.core" % Version,