name := "org.tmt.csw.cs.core"

organization := Organization

version := Version

scalaVersion := SrcScalaVersion

libraryDependencies ++= Seq(
    "org.tmt" %% "org.tmt.csw.cs.api" % Version,
    "org.eclipse.jgit" % "org.eclipse.jgit" % "2.3.1.201302201838-r",
    "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
    "org.osgi" % "org.osgi.core" % "4.3.0" % "provided",
    "biz.aQute" % "bndlib" % "2.0.0.20130123-133441",
    "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test",
    "junit" % "junit" % "4.10" % "test"
)

osgiSettings

OsgiKeys.exportPackage := Seq(
    "org.tmt.csw.cs.core",
    "org.tmt.csw.cs.core.git"
)

OsgiKeys.additionalHeaders := Map(
	"Service-Component" -> "*"
)
