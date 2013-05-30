name := "org.tmt.csw.cmd.akka"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test",
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test"
)
