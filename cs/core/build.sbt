name := "org.tmt.csw.cs.core"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    csApi,
    jgit,
    scalaLogging,
    logback,
    scalaIoFile,
    scalaTest,
    junit
)
