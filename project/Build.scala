import sbt._
import Keys._
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.packager.Keys._


// This is the top level build object used by sbt.
object Build extends Build {
  import Settings._
  import Dependencies._

  // Shared utils
  lazy val util = project
    .settings(defaultSettings: _*)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(scalaLogging, logback) ++
      test(liftJSON, scalaTest, specs2, akkaTestKit, junit)
    )

  // Config Service
  lazy val cs = project
  	.settings(defaultSettings: _*)
  	.settings(libraryDependencies ++=
  		provided(akkaActor) ++
      compile(jgit, scalaIoFile, scalaLogging, logback) ++
      test(scalaTest, specs2, akkaTestKit, junit)
  	)

  // Command Service
  lazy val kvs = project
    .settings(defaultSettings: _*)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(redisScala, scalaLogging, logback) ++
      test(scalaTest, specs2, akkaTestKit, junit)
    ) dependsOn util

  // Location Service
  lazy val loc = project
    .settings(packageSettings: _*)
    .settings(libraryDependencies ++= provided(akkaActor) ++
    compile(akkaKernel, akkaRemote, scalaLogging, logback) ++
    test(scalaTest, specs2, akkaTestKit, junit))

  // Command Service
  lazy val cmd = project
    .settings(defaultSettings: _*)
    .settings(twirlSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      compile(scalaLogging, logback, sprayRouting, sprayJson, sprayCan, sprayClient) ++
      test(liftJSON, scalaTest, specs2, akkaTestKit, junit, sprayTestkit)
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
      test(scalaTest, specs2, akkaTestKit, junit)
    ) dependsOn util

  // -- Apps --

  // Build the containerCmd command line application
  lazy val containerCmd = Project(id = "containerCmd", base = file("apps/containerCmd"))
    .settings(packageSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      compile(akkaKernel, akkaRemote) ++
      test(scalaLogging, logback)
    ) dependsOn(pkg, cmd, loc, util, container1, container2)


  // -- Test subprojects with dependency information --

  lazy val container1 = Project(
    id = "container1",
    base = file("test/pkg/container1")
  ).settings(packageSettings: _*)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaKernel, akkaRemote)
    ).dependsOn(pkg, cmd, loc, util)

  lazy val container2 = Project(
    id = "container2",
    base = file("test/pkg/container2")
  ).settings(packageSettings: _*)
    .settings(bashScriptExtraDefines ++= Seq(s"addJava -Dcsw.extjs.root=" + file("extjs").absolutePath))
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(akkaKernel, akkaRemote, akkaZeromq)
    ).dependsOn(pkg, cmd, loc, util)
}
