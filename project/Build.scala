import sbt._
import Keys._
import akka.sbt.AkkaKernelPlugin._
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

// This is the top level build object used by sbt.
object Build extends Build {
  import Settings._
  import Dependencies._

  // Base project
  lazy val csw = project.in(file("."))
    .aggregate(cs, cmd, pkg, ls, test_app, test_client, container1, container2)
    .settings(buildSettings: _*)


  // Config Service
  lazy val cs = project
  	.settings(buildSettings: _*)
  	.settings(libraryDependencies ++=
  		provided(akkaActor) ++
      compile(jgit, scalaIoFile, scalaLogging, logback) ++
      test(scalaTest, specs2, akkaTestKit, junit)
  	)

  // Location Service
  lazy val ls = Project(id = "ls", base = file("ls"),
    settings = defaultSettings ++ distSettings ++
      Seq(distJvmOptions in Dist := "-Xms256M -Xmx1024M",
        distBootClass in Dist := "org.tmt.csw.ls.LocationService",
        outputDirectory in Dist := file("ls/target"),
        libraryDependencies ++=
          provided(akkaActor) ++
            compile(akkaKernel, akkaRemote, scalaLogging, logback) ++
            test(scalaTest, specs2, akkaTestKit, junit)
      )
  )

  // Command Service
  lazy val cmd = project
    .settings(buildSettings: _*)
    .settings(twirlSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      compile(scalaLogging, logback, sprayRouting, sprayJson, sprayCan, sprayClient) ++
      test(liftJSON, scalaTest, specs2, akkaTestKit, junit, sprayTestkit)
    ) dependsOn ls


  // Package (Container, Component) classes
  lazy val pkg = project
    .settings(buildSettings: _*)
    .settings(multiJvmSettings: _*)
    .dependsOn(cmd, ls)
    .settings(libraryDependencies ++=
    provided(akkaActor) ++
      compile(scalaLogging, logback) ++
      test(scalaTest, akkaTestKit, akkaMultiNodeTest)
    ) configs MultiJvm


  // -- Test subprojects with dependency information --

  // test-app/app (server, see ../test/test-app/README.md)
  lazy val test_app = Project(
    id = "test-app",
    base = file("test/test-app/app"),
    settings = defaultSettings ++ distSettings ++
      Seq(distJvmOptions in Dist := "-Xms256M -Xmx1024M",
          distBootClass in Dist := "org.tmt.csw.test.app.TestApp",
          outputDirectory in Dist := file("test/test-app/app/target"),
          libraryDependencies ++=
            provided(akkaActor) ++
            compile(akkaKernel, akkaRemote) ++
            test(scalaLogging, logback)
      )
    ) dependsOn(cs, cmd, ls)


  // test-app/client (see ../test/test-app/README.md)
  lazy val test_client = Project(
    id = "test-client",
    base = file("test/test-app/client"),
    settings = defaultSettings ++ distSettings ++
      Seq(distJvmOptions in Dist := "-Xms256M -Xmx1024M",
          distBootClass in Dist := "org.tmt.csw.test.client.TestClient",
          outputDirectory in Dist := file("test/test-app/client/target"),
          libraryDependencies ++=
            provided(akkaActor) ++
            compile(akkaKernel, akkaRemote) ++
            test(scalaLogging, logback)
      )
    ) dependsOn(cs, cmd, ls)


  // pkg test/demo: Container1 (see ../test/pkg/README.md)
  lazy val container1 = Project(
    id = "container1",
    base = file("test/pkg/container1"),
    settings = defaultSettings ++ distSettings ++
      Seq(distJvmOptions in Dist := "-Xms256M -Xmx1024M -Dcsw.extjs.root=" + file("extjs").absolutePath,
        distBootClass in Dist := "org.tmt.csw.test.container1.Container1",
        outputDirectory in Dist := file("test/pkg/container1/target"),
        libraryDependencies ++=
          provided(akkaActor) ++
            compile(akkaKernel, akkaRemote) ++
            test(scalaLogging, logback)
      )
  ) dependsOn(pkg, cs, cmd, ls)


  // pkg test/demo: Container2 (see ../test/pkg/README.md)
  lazy val container2 = Project(
    id = "container2",
    base = file("test/pkg/container2"),
    settings = defaultSettings ++ distSettings ++
      Seq(distJvmOptions in Dist := "-Xms256M -Xmx1024M",
        distBootClass in Dist := "org.tmt.csw.test.container2.Container2",
        outputDirectory in Dist := file("test/pkg/container2/target"),
        libraryDependencies ++=
          provided(akkaActor) ++
            compile(akkaKernel, akkaRemote, akkaZeromq) ++
            test(scalaLogging, logback)
      )
  ) dependsOn(pkg, cs, cmd, ls)
}


