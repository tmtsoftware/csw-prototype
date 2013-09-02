import sbt._
import Keys._
import akka.sbt.AkkaKernelPlugin._

// This is the top level build object used by sbt.
object Build extends Build {
  import Settings._
  import Dependencies._

  // Base project
  lazy val root = Project(id = "csw", base = file("."))
    .aggregate(cs, cmd, test_app, test_client)
    .settings(buildSettings: _*)

  lazy val cs = Project(id = "cs", base = file("cs"))
  	.settings(buildSettings: _*)
  	.settings(libraryDependencies ++=
  		provided(akkaActor) ++
      compile(jgit, scalaIoFile, scalaLogging, logback) ++
      test(scalaTest, specs2, akkaTestKit, junit)
  	)

  lazy val cmd = Project(id = "cmd", base = file("cmd"))
    .settings(buildSettings: _*)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      compile(scalaLogging, logback, sprayRouting, sprayJson, sprayCan) ++
      test(liftJSON, scalaTest, specs2, akkaTestKit, junit, sprayTestkit)
    )

  lazy val pkg = Project(id = "pkg", base = file("pkg"))
    .settings(buildSettings: _*)
    .dependsOn(cmd)
    .settings(libraryDependencies ++=
      provided(akkaActor) ++
      compile(scalaLogging, logback) ++
      test(scalaTest, akkaTestKit)
    )


// top level Test project
  lazy val defaultSettings = buildSettings ++ Seq(
    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
  )

  lazy val akkaKernelPluginSettings = Seq(
    distJvmOptions in Dist := "-Xms256M -Xmx1024M",
    distBootClass in Dist := "org.tmt.csw.test.app.TestApp",
    outputDirectory in Dist := file("target/test-app")
  )

  // Test subprojects with dependency information
  // Test application
  lazy val test_app = Project(
    id = "test-app",
    base = file("test/app"),
    settings = defaultSettings ++ distSettings ++
      Seq(distJvmOptions in Dist := "-Xms256M -Xmx1024M",
          distBootClass in Dist := "org.tmt.csw.test.app.TestApp",
          outputDirectory in Dist := file("target/test-app"),
          libraryDependencies ++=
            provided(akkaActor) ++
            compile(akkaKernel, akkaRemote) ++
            test(scalaLogging, logback)
      )
    ) dependsOn(cs, cmd)

  // Test client
  lazy val test_client = Project(
    id = "test-client",
    base = file("test/client"),
    settings = defaultSettings ++ distSettings ++
      Seq(distJvmOptions in Dist := "-Xms256M -Xmx1024M",
          distBootClass in Dist := "org.tmt.csw.test.client.TestClient",
          outputDirectory in Dist := file("target/test-client"),
          libraryDependencies ++=
            provided(akkaActor) ++
            compile(akkaKernel, akkaRemote) ++
            test(scalaLogging, logback)
      )
    ) dependsOn(cs, cmd)



}


