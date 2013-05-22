import akka.sbt.AkkaKernelPlugin._
import sbt._
import Keys._

// Defines the sbt build for the Test (Integration Test) subproject.
// This can also serve as an example standalone application setup.
// (See http://doc.akka.io/docs/akka/snapshot/scala/microkernel.html)
trait Test extends Build with Settings with Cs {

  // top level Test project
  lazy val test = Project(id = "test", base = file("test")) aggregate (test_app)

  val testAppDependencies = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-kernel" % AkkaVersion,
    "org.tmt" %% "org.tmt.csw.cs.akka" % Version,
    "org.tmt" %% "org.tmt.csw.cs.api" % Version,
    "org.tmt" %% "org.tmt.csw.cs.core" % Version,
    "org.osgi" % "org.osgi.core" % "4.3.0" % "provided"
  )

  // Test subprojects with dependency information
  lazy val test_app = Project(
    id = "test-app",
    base = file("test/app"),
    settings = defaultSettings ++ distSettings ++ Seq(
      libraryDependencies ++= testAppDependencies,
      distJvmOptions in Dist := "-Xms256M -Xmx1024M",
      distBootClass in Dist := "org.tmt.csw.test.app.TestApp",
      outputDirectory in Dist := file("target/test-app")
    )
  ) dependsOn(cs_akka, cs_api, cs_core)

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := Organization,
    version := Version,
    scalaVersion := SrcScalaVersion,
    crossPaths := false,
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org"))
  )

  lazy val defaultSettings = buildSettings ++ Seq(
    resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",

    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
  )
}
