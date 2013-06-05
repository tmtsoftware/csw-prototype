name := "org.tmt.csw.cs.core"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    "org.tmt" %% "org.tmt.csw.cs.api" % Version,
    "org.eclipse.jgit" % "org.eclipse.jgit" % "2.3.1.201302201838-r",
    "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test"
)
