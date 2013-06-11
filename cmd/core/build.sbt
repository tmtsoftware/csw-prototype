name := "org.tmt.csw.cmd.core"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    typesafeConfig,
    scalaLogging,
    logback,
    scalaTest,
    "net.liftweb" %% "lift-json" % "2.5" % "test"
)
