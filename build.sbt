import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtSite.site
import sbt.Keys._
import sbt._
import com.typesafe.sbt.packager.Keys._
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
    test(scalaTest)
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
    compile(akkaSlf4j, scalaLogging, logback, janino, logstashLogbackEncoder)
  )

// Key Value Store
lazy val kvs = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, redisScala, logback) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(util, log)


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
  ) dependsOn(log, loc, kvs, util % "compile->compile;test->test")

// Config Service
lazy val cs = project
  .settings(defaultSettings: _*)
  .settings(packageSettings("CSW Config Service", "Used to manage configuration files in a Git repository"): _*)
  .settings(bashScriptExtraDefines ++= Seq("addJava -Dapplication-name=configService"))
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .dependsOn(log, loc, util, configServiceAnnex)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaHttpSprayJson, jgit, logback, akkaHttp, scopt) ++
      test(scalaTest, akkaTestKit, junit, akkaMultiNodeTest)
  ) configs MultiJvm

// Package (Container, Component) classes
lazy val pkg = project
  .settings(defaultSettings: _*)
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .dependsOn(ccs % "compile->compile;test->test", util % "compile->compile;test->test", log, loc)
  .settings(libraryDependencies ++=
    compile(akkaActor) ++
      test(scalaTest, akkaTestKit, akkaMultiNodeTest)
  ) configs MultiJvm


// Event Service
lazy val event = project
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
  .settings(packageSettings("CSW Sequencer", "Scala REPL for running sequences"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, scalaLibrary, scalaCompiler, scalaReflect, jline)
  ) dependsOn(pkg, ccs, loc, log)

// Build the config service annex application
lazy val configServiceAnnex = Project(id = "configServiceAnnex", base = file("apps/configServiceAnnex"))
  .settings(packageSettings("CSW Config Service Annex", "Store/retrieve large files for Config Service"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, akkaHttp) ++
      test(scalaTest, specs2, akkaTestKit)
  ) dependsOn(loc, log, util)

// Build the config service annex application
lazy val csClient = Project(id = "csClient", base = file("apps/csClient"))
  .settings(packageSettings("CSW Config Service Client", "Command line client for Config Service"): _*)
  .settings(libraryDependencies ++=
    compile(akkaActor, akkaRemote, akkaStream, scopt) ++
      test(scalaTest, specs2, akkaTestKit)
  ) dependsOn cs


// Need a root project for unidoc plugin, so we can merge the scaladocs
val csw = (project in file(".")).
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
  ).
  aggregate(util, support, log, kvs, loc, ccs, cs, pkg, event, ts,
    containerCmd, sequencer, configServiceAnnex, csClient)
