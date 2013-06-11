name := "org.tmt.csw.cmd.akka"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    "org.tmt" %% "org.tmt.csw.cmd.core" % Version,
    akkaActor,
    akkaTestKit,
    scalaTest
)
