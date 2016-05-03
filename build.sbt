import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtSite.site
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


// Utility classes
lazy val util = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, scalaReflect) ++
    test(scalaTest, junit)
  )

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
    compile(akkaSlf4j, scalaLogging, logback, janino, logstashLogbackEncoder, akkaKryo)
  )

// Key Value Store
lazy val kvs = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, redisScala, logback, akkaKryo) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(util, log)


// Location Service
lazy val loc = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, jmdns, akkaHttp, akkaKryo) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn log

// Command Service
lazy val ccs = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaSse, akkaKryo) ++
      test(scalaTest, specs2, akkaTestKit, akkaStreamTestKit, akkaHttpTestKit)
  ) dependsOn(log, loc, kvs, util)

// Config Service
lazy val cs = project
  .settings(defaultSettings: _*)
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("configService", "CSW Config Service", "Used to manage configuration files in a Git repository"): _*)
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .dependsOn(log, loc, util, configServiceAnnex)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaHttpSprayJson, jgit, svnkit, logback, akkaHttp, scopt, akkaKryo) ++
      test(scalaTest, akkaTestKit, junit, akkaMultiNodeTest)
  ) configs MultiJvm

// Package (Container, Component) classes
lazy val pkg = project
  .settings(defaultSettings: _*)
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .dependsOn(log, loc, ccs, util, ts % "test", event % "test", kvs % "test")
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaKryo) ++
      test(scalaTest, akkaTestKit, akkaMultiNodeTest)
  ) configs MultiJvm


// Event Service
lazy val event = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, hornetqServer, hornetqNative, ficus, akkaKryo) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(util, log)

// Time Service support
lazy val ts = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaKryo) ++
      test(scalaTest, akkaTestKit, junit)
  ) dependsOn log

// Java APIs
lazy val javacsw = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor) ++
      test(akkaTestKit, junit)
  ) dependsOn(util, support, log, kvs, loc, ccs, cs, pkg, event, ts)


// -- Apps --

// Build the containerCmd command line application
lazy val containerCmd = Project(id = "containerCmd", base = file("apps/containerCmd"))
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, scopt, akkaKryo) ++
      test(scalaLogging, logback)
  ) dependsOn(pkg, ccs, loc, log, cs)

// Build the sequencer command line application
lazy val sequencer = Project(id = "sequencer", base = file("apps/sequencer"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("sequencer", "CSW Sequencer", "Scala REPL for running sequences"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, scalaLibrary, scalaCompiler, scalaReflect, jline, akkaKryo)
  ) dependsOn(pkg, ccs, loc, log, hcdExample)

// Build the config service annex application
lazy val configServiceAnnex = Project(id = "configServiceAnnex", base = file("apps/configServiceAnnex"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("configServiceAnnex", "CSW Config Service Annex", "Store/retrieve large files for Config Service"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, akkaHttp, akkaKryo) ++
      test(scalaTest, specs2, akkaTestKit)
  ) dependsOn(loc, log, util)

// Build the config service annex application
lazy val csClient = Project(id = "csClient", base = file("apps/csClient"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("csClient", "CSW Config Service Client", "Command line client for Config Service"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, akkaStream, scopt, akkaKryo) ++
      test(scalaTest, specs2, akkaTestKit)
  ) dependsOn cs

// HCD Example project
lazy val hcdExample = Project(id = "hcdExample", base = file("examples/hcdExample"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("hcdExample", "HCD Example", "Simple HCD example application"): _*)
  .settings(mainClass in Compile := Some("csw.examples.HCDExampleApp"))
  .dependsOn(pkg, ts, event)

// Assembly Example project
lazy val assemblyExample = Project(id = "assemblyExample", base = file("examples/assemblyExample"))
  .enablePlugins(JavaAppPackaging)
  .settings(packageSettings("assemblyExample", "Assembly Example", "Simple Assembly example application"): _*)
  .dependsOn(pkg, ts, hcdExample)

// Need a root project for unidoc plugin, so we can merge the scaladocs
val csw = (project in file(".")).
  configs(JavaDoc).
  settings(defaultSettings: _*).
  settings(siteSettings: _*).
  settings(unidocSettings: _*).
  settings(
    name := "CSW - TMT Common Software",
    site.addMappingsToSiteDir(mappings in(ScalaUnidoc, packageDoc), "latest/api"),
    preprocessVars := Map(
      "CSWSRC" -> s"https://github.com/tmtsoftware/csw/tree/${git.gitCurrentBranch.value}",
      "DOCROOT" -> "latest/api/index.html"
    )
  ).aggregate(util, support, log, kvs, loc, ccs, cs, pkg, event, ts,
    containerCmd, sequencer, configServiceAnnex, csClient, hcdExample, assemblyExample, javacsw)
