name := "org.tmt.csw.cs.akka"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    csApi,
    csCore,
    akkaActor,
    akkaTestKit,
    scalaTest
)
