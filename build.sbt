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
  ).aggregate(util, support, log, events, event_old, alarms, loc, ccs, cs, pkg, ts,
  containerCmd, sequencer, configServiceAnnex, csClient, hcdExample, assemblyExample, trackLocation, asConsole, sysControl, javacsw)

// Utility classes
lazy val util = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaHttpSprayJson, scalaReflect) ++
      test(scalaTest, junit)
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
    compile(akkaActor, redisScala, logback) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(util, log)

// Alarm Service
lazy val alarms = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaHttpSprayJson, redisScala, jsonSchemaValidator, ficus) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(util, log, loc, trackLocation % "test->test")

// Location Service
lazy val loc = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, jmdns, akkaHttp) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn log

// Command Service
lazy val ccs = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaSse) ++
      test(scalaTest, specs2, akkaTestKit, akkaStreamTestKit, akkaHttpTestKit)
  ) dependsOn(log, loc, events, util)

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
    compile(akkaActor, akkaHttpSprayJson, jgit, svnkit, logback, akkaHttp, scopt) ++
      test(scalaTest, akkaTestKit, junit, akkaMultiNodeTest)
  ) configs MultiJvm

// Package (Container, Component) classes
lazy val pkg = project
  .settings(defaultSettings: _*)
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .dependsOn(log, loc, ccs, util, ts % "test")
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
      test(scalaTest, akkaTestKit, junit)
  ) dependsOn log

// Java APIs
lazy val javacsw = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor) ++
      test(akkaTestKit, junit, junitInterface, scalaJava8Compat, assertj)
  ) dependsOn(util, support, log, events, loc, ccs, cs, pkg, event_old, ts, containerCmd,
  events % "test->test", alarms % "test->test;compile->compile", trackLocation % "test->test")

// -- Apps --

// Build the containerCmd command line application
lazy val containerCmd = Project(id = "containerCmd", base = file("apps/containerCmd"))
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, scopt) ++
      test(scalaLogging, logback)
  ) dependsOn(pkg, ccs, loc, log, cs)

// Build the sequencer command line application
lazy val sequencer = Project(id = "sequencer", base = file("apps/sequencer"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("sequencer", "CSW Sequencer", "Scala REPL for running sequences"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, scalaLibrary, scalaCompiler, scalaReflect, jline)
  ) dependsOn(pkg, ccs, loc, log, hcdExample)

// Build the config service annex application
lazy val configServiceAnnex = Project(id = "configServiceAnnex", base = file("apps/configServiceAnnex"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("configServiceAnnex", "CSW Config Service Annex", "Store/retrieve large files for Config Service"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, akkaHttp) ++
      test(scalaTest, specs2, akkaTestKit)
  ) dependsOn(loc, log, util)

// Track the location of an external application
lazy val trackLocation = Project(id = "trackLocation", base = file("apps/trackLocation"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("trackLocation", "Location Service Application Tracker", "Track Location"): _*)
  .settings(libraryDependencies ++=
    compile(scopt, akkaActor) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(loc, log, cs % "test->test;compile->compile", events % "test->test")

// Track the location of an external application
lazy val asConsole = Project(id = "asConsole", base = file("apps/asConsole"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("asConsole", "Alarm Service Console application", "Alarm Service Console"): _*)
  .settings(libraryDependencies ++=
    compile(scopt, akkaActor) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(loc, log, alarms, trackLocation % "test->test")

// Track the location of an external application
lazy val sysControl = Project(id = "sysControl", base = file("apps/sysControl"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("sysControl", "System remote control for CSW services", "System Remote Control"): _*)
  .settings(libraryDependencies ++=
    compile(scopt, akkaActor) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(loc, log, pkg, cs % "test->test;compile->compile")

// Build the config service annex application
lazy val csClient = Project(id = "csClient", base = file("apps/csClient"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("csClient", "CSW Config Service Client", "Command line client for Config Service"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, akkaStream, scopt) ++
      test(scalaTest, specs2, akkaTestKit)
  ) dependsOn cs

// HCD Example project
lazy val hcdExample = Project(id = "hcdExample", base = file("examples/hcdExample"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("hcdExample", "HCD Example", "Simple HCD example application"): _*)
  .settings(mainClass in Compile := Some("csw.examples.HCDExampleApp"))
  .dependsOn(pkg, ts, events)

// Assembly Example project
lazy val assemblyExample = Project(id = "assemblyExample", base = file("examples/assemblyExample"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("assemblyExample", "Assembly Example", "Simple Assembly example application"): _*)
  .dependsOn(pkg, ts, hcdExample)

// EndToEnd Example project
lazy val vslice = Project(id = "vslice", base = file("examples/vslice"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("VerticalSlice", "Vertical Slice Example", "More complicated example showing CSW features"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, akkaHttp) ++
      test(scalaTest, specs2, akkaTestKit)
  ).dependsOn(pkg, cs, ccs, loc, ts, events, util)

// EndToEnd Example project Java version
lazy val vsliceJava = Project(id = "vsliceJava", base = file("examples/vsliceJava"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("VerticalSliceJava", "Vertical Slice Java Example", "More complicated example showing CSW Java features"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, akkaHttp) ++
      test(akkaTestKit, junit, junitInterface, scalaJava8Compat)
  ).dependsOn(javacsw)
