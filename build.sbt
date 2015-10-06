import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import play.twirl.sbt.SbtTwirl
import sbt.Keys._
import sbt._
import com.typesafe.sbt.packager.Keys._

import Dependencies._
import Settings._

def compile(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")

def provided(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")

def test(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")

def runtime(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")

def container(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")


// Contains classes that are shared between the scala and scala.js code
lazy val shared = (crossProject.crossType(CrossType.Pure) in file("shared"))
  .settings(defaultSettings: _*)
  .settings(fork := false)

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

// Utility classes
lazy val util = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaHttpSprayJson, akkaHttp, scalaLogging, logback, protobufJava, scalaPickling) ++
      test(scalaTest, akkaTestKit)
  )

// Supporting classes
lazy val support = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(scalaLogging, logback) ++
      test(scalaTest)
  )

// Logging support, Log service (only includes config files so far)
lazy val log = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaSlf4j, logback, janino, logstashLogbackEncoder)
  )

// Key Value Store
lazy val kvs_old = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(redisScala, scalaLogging, logback) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn util

lazy val kvs = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(redisScala, scalaLogging, logback) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn util


// Location Service (old)
lazy val loc_old = project
  .settings(packageSettings("CSW Location Service", "Used to lookup command service actors"): _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaRemote) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(log, util)

// Location Service (new)
lazy val loc = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaRemote, jmdns, akkaHttp, scalaLogging, logback) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn log

// Command Service (old)
lazy val cmd_old = project.enablePlugins(SbtTwirl)
  .settings(defaultSettings: _*)
  .settings(twirlSettings: _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(scalaLogging, logback, akkaSse, upickle) ++
      test(scalaTest, specs2, akkaTestKit, akkaStreamTestKit, akkaHttpTestKit)
  ) dependsOn(sharedJvm, loc_old, util % "compile->compile;test->test")

// Command Service (new)
lazy val ccs = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(scalaLogging, logback, akkaSse, upickle) ++
      test(scalaTest, specs2, akkaTestKit, akkaStreamTestKit, akkaHttpTestKit)
  ) dependsOn(sharedJvm, loc, kvs, util % "compile->compile;test->test")

// Config Service
lazy val cs = project
  .settings(defaultSettings: _*)
  .settings(packageSettings("CSW Config Service", "Used to manage configuration files in a Git repository"): _*)
  .settings(bashScriptExtraDefines ++= Seq("addJava -Dapplication-name=configService"))
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .dependsOn(log, loc_old, util, configServiceAnnex)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(jgit, scalaLogging, logback, akkaHttp, scopt) ++
      test(scalaTest, akkaTestKit, junit, akkaMultiNodeTest)
  ) configs MultiJvm

// Package (Container, Component) classes
lazy val pkg_old = project
  .settings(defaultSettings: _*)
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .dependsOn(cmd_old % "compile->compile;test->test", util % "compile->compile;test->test", loc_old)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(scalaLogging, logback) ++
      test(scalaTest, akkaTestKit, akkaMultiNodeTest)
  ) configs MultiJvm

lazy val pkg = project
  .settings(defaultSettings: _*)
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .dependsOn(ccs % "compile->compile;test->test", util % "compile->compile;test->test", loc)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(scalaLogging, logback) ++
      test(scalaTest, akkaTestKit, akkaMultiNodeTest)
  ) configs MultiJvm


// Event Service
lazy val event = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(hornetqServer, hornetqNative, ficus, scalaLogging, logback) ++
      test(scalaTest, akkaTestKit)
  ) dependsOn(util, log)

// -- Apps --

// Build the containerCmd command line application
lazy val containerCmd = Project(id = "containerCmd", base = file("apps/containerCmd"))
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaRemote, scopt) ++
      test(scalaLogging, logback)
  ) dependsOn(pkg_old, cmd_old, loc_old, util, cs)

// Build the sequencer command line application
lazy val sequencer = Project(id = "sequencer", base = file("apps/sequencer"))
  .settings(packageSettings("CSW Sequencer", "Scala REPL for running sequences"): _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaRemote, scalaLibrary, scalaCompiler, scalaReflect, jline) ++
      test(scalaLogging, logback)
  ) dependsOn(pkg_old, cmd_old, loc_old, util)

// Build the config service annex application
lazy val configServiceAnnex = Project(id = "configServiceAnnex", base = file("apps/configServiceAnnex"))
  .settings(packageSettings("CSW Config Service Annex", "Store/retrieve large files for Config Service"): _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaRemote, akkaHttp) ++
      test(scalaLogging, logback, scalaTest, specs2, akkaTestKit)
  ) dependsOn(loc_old, util)

// Build the config service annex application
lazy val csClient = Project(id = "csClient", base = file("apps/csClient"))
  .settings(packageSettings("CSW Config Service Client", "Command line client for Config Service"): _*)
  .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaRemote, akkaStream, scopt) ++
      test(scalaLogging, logback, scalaTest, specs2, akkaTestKit)
  ) dependsOn cs
