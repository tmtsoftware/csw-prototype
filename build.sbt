import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import sbt.Keys._
import sbt._
import com.typesafe.sbt.site.PreprocessSupport._

import Dependencies._
import Settings._

def compile(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")

def test(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")

def runtime(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")

def container(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")


// Need a root project for unidoc plugin, so we can merge the scaladocs
val csw = (project in file("."))
  .enablePlugins(cswbuild.UnidocRoot)
  .settings(UnidocRoot.settings(Nil, Nil): _*)
  .settings(defaultSettings: _*)
  .settings(siteSettings: _*)
  .settings(
    name := "CSW - TMT Common Software",
    preprocessVars := Map(
      "CSWSRC" -> s"https://github.com/tmtsoftware/csw/tree/${git.gitCurrentBranch.value}",
      "DOCROOT" -> "latest/api/index.html"
    )
  ).aggregate(util, support, log, loc, events, event_old, alarms, ccs, cs, pkg, ts,
  containerCmd, sequencer, configServiceAnnex, csClient, hcdExample, assemblyExample,
  trackLocation, asConsole, sysControl, seqSupport, javacsw
)

// Utility classes
lazy val util = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaHttpSprayJson, scalaReflect) ++
      test(scalaTest)
  ) dependsOn log

// AAS- Authorization and Authentication service
lazy val aas = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor) ++
      test(scalaTest)
  ) dependsOn log

// Database service
lazy val dbs = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(slick, postgresql, HikariCP) ++
      test(scalaTest)
  ) dependsOn log

// Supporting classes
lazy val support = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    test(scalaTest)
  )

// Logging support, Log service (only includes config files so far)
lazy val log = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaSlf4j, scalaLogging, logback, janino, logstashLogbackEncoder) ++
      test(scalaTest, akkaTestKit)
  )

// Event Service and Key Value Store
lazy val events = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, redisScala) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(util, log, loc, trackLocation)

// Alarm Service
lazy val alarms = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaHttpSprayJson, redisScala, jsonSchemaValidator, ficus) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(util, log, loc, trackLocation)

// Location Service
lazy val loc = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, jmdns) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn log

// Command Service
lazy val ccs = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(log, loc, util)

// Config Service
lazy val cs = project
  .settings(defaultSettings: _*)
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("configService", "CSW Config Service", "Used to manage configuration files in a Git repository"): _*)
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .settings(configServiceDockerSettings: _*)
  .dependsOn(log, loc, util, configServiceAnnex)
  .enablePlugins(DockerPlugin)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaHttpSprayJson, jgit, svnkit, akkaHttp, scopt) ++
      test(scalaTest, akkaTestKit, akkaMultiNodeTest)
  ) configs MultiJvm

// Package (Container, Component) classes
lazy val pkg = project
  .settings(defaultSettings: _*)
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .dependsOn(log, loc, util, ccs % "test")
  .settings(libraryDependencies ++=
    compile(akkaActor) ++
      test(scalaTest, akkaTestKit, akkaMultiNodeTest)
  ) configs MultiJvm


// Event Service
lazy val event_old = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, hornetqServer, hornetqNative, ficus) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(util, log)

// Time Service support
lazy val ts = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn log

// Java APIs
lazy val javacsw = project
  .settings(defaultSettings: _*)
  .settings(// fix problems with javadoc errors?
    publishArtifact in(Compile, packageDoc) := false,
    publishArtifact in packageDoc := false,
    sources in(Compile, doc) := Seq.empty
  )
  .settings(libraryDependencies ++=
    compile(akkaActor) ++
      test(akkaTestKit, junitInterface, scalaJava8Compat, assertj)
  ) dependsOn(util, support, log, events, loc, ccs, cs, pkg, event_old, ts, containerCmd,
  events % "test->test", alarms % "test->test;compile->compile", trackLocation % "test->test")

// Runtime library for use in sequencer scripts
lazy val seqSupport = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor) ++
      test(akkaTestKit, assertj)
  ) dependsOn(util, support, pkg, ccs, cs, loc, log, ts, events, alarms, containerCmd, hcdExample)


// -- Apps --

// Build the containerCmd command line application
lazy val containerCmd = Project(id = "containerCmd", base = file("apps/containerCmd"))
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, scopt)
  ) dependsOn(pkg, ccs, loc, log, cs)

// Build the sequencer command line application
lazy val sequencer = Project(id = "sequencer", base = file("apps/sequencer"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("sequencer", "CSW Sequencer", "Scala REPL for running sequences"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, scalaLibrary, scalaCompiler, scalaReflect, jline)
  ) dependsOn(pkg, cs, ccs, loc, ts, events, util, alarms, containerCmd, seqSupport)

// Build the config service annex application
lazy val configServiceAnnex = Project(id = "configServiceAnnex", base = file("apps/configServiceAnnex"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("configServiceAnnex", "CSW Config Service Annex", "Store/retrieve large files for Config Service"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, akkaHttp) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(loc, log, util)

// Track the location of an external application
lazy val trackLocation = Project(id = "trackLocation", base = file("apps/trackLocation"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("trackLocation", "Location Service Application Tracker", "Track Location"): _*)
  .settings(libraryDependencies ++=
    compile(scopt, akkaActor) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(loc, log, cs % "test->test;compile->compile")

// Track the location of an external application
lazy val asConsole = Project(id = "asConsole", base = file("apps/asConsole"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("asConsole", "Alarm Service Console application", "Alarm Service Console"): _*)
  .settings(libraryDependencies ++=
    compile(scopt, akkaActor) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(loc, log, alarms, trackLocation)

// Track the location of an external application
lazy val sysControl = Project(id = "sysControl", base = file("apps/sysControl"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("sysControl", "System remote control for CSW services", "System Remote Control"): _*)
  .settings(libraryDependencies ++=
    compile(scopt, akkaActor) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(loc, log, pkg, cs)

// Build the config service client application
lazy val csClient = Project(id = "csClient", base = file("apps/csClient"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("csClient", "CSW Config Service Client", "Command line client for Config Service"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, scopt) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn cs


//  -- Example projects --


// HCD Example project
lazy val hcdExample = Project(id = "hcdExample", base = file("examples/hcdExample"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("hcdExample", "HCD Example", "Simple HCD example application"): _*)
  .settings(mainClass in Compile := Some("csw.examples.HCDExampleApp"))
  .dependsOn(pkg, ccs, ts, events)

// Assembly Example project
lazy val assemblyExample = Project(id = "assemblyExample", base = file("examples/assemblyExample"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("assemblyExample", "Assembly Example", "Simple Assembly example application"): _*)
  .dependsOn(pkg, ccs, ts, hcdExample)

