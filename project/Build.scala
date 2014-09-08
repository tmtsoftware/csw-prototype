import com.typesafe.sbt.packager.Keys._
import sbt._
import Keys._
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import play.twirl.sbt.SbtTwirl

// This is the top level build object used by sbt.
object Build extends Build {

  import Settings._
  import Dependencies._

  // Shared utils
  lazy val util = project
    .settings(defaultSettings: _*)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(sprayJson, sprayHttpx, scalaLogging, logback, protobufJava) ++
      test(scalaTest, akkaTestKit)
    )

  // Support classes
  lazy val support = project
    .settings(defaultSettings: _*)
    .settings(libraryDependencies ++=
    compile(scalaLogging, logback) ++
      test(scalaTest)
    )

  // Config Service
  lazy val cs = project
    .settings(defaultSettings: _*)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(jgit, scalaLogging, logback) ++
      test(scalaTest, akkaTestKit, junit)
    )

  // Logging support, Log service (only includes config files so far)
  lazy val log = project
    .settings(defaultSettings: _*)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaSlf4j, logback, janino, logstashLogbackEncoder)
    )

  // Key Value Store
  lazy val kvs = project
    .settings(defaultSettings: _*)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(redisScala, scalaLogging, logback) ++
      test(scalaTest, akkaTestKit)
    ) dependsOn util

  // Location Service
  lazy val loc = project
    .settings(packageSettings("CSW Location Service", "Used to lookup command service actors"): _*)
    .settings(bashScriptExtraDefines ++= Seq("addJava -Dapplication-name=loc"))
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaRemote) ++
      test(scalaTest, akkaTestKit)
    ) dependsOn log

  // Command Service
  lazy val cmd = project.enablePlugins(SbtTwirl)
    .settings(defaultSettings: _*)
    .settings(twirlSettings: _*)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(scalaLogging, logback, sprayRouting, sprayJson, sprayCan, sprayClient) ++
      test(scalaTest, specs2, akkaTestKit, sprayTestkit)
    ) dependsOn(loc, util % "compile->compile;test->test")

  // Package (Container, Component) classes
  lazy val pkg = project
    .settings(defaultSettings: _*)
    .settings(multiJvmSettings: _*)
    .dependsOn(cmd % "compile->compile;test->test", util % "compile->compile;test->test", loc)
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
      compile(hornetqServer, hornetqNative, scalaLogging, logback) ++
      test(scalaTest, akkaTestKit)
    ) dependsOn util

  // -- Apps --

  // Build the containerCmd command line application
  lazy val containerCmd = Project(id = "containerCmd", base = file("apps/containerCmd"))
    .settings(packageSettings("CSW Container Command", "Used to configure and start CSW containers"): _*)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaRemote) ++
      test(scalaLogging, logback)
    ) dependsOn(pkg, cmd, loc, util)

  // Build the sequencer command line application
  lazy val sequencer = Project(id = "sequencer", base = file("apps/sequencer"))
    .settings(packageSettings("CSW Sequencer", "Scala REPL for running sequences"): _*)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaRemote, scalaLibrary, scalaCompiler, scalaReflect, jline) ++
      test(scalaLogging, logback)
    ) dependsOn(pkg, cmd, loc, util)
}
