import akka.sbt.AkkaKernelPlugin._
import sbt._
import Keys._

// Defines the sbt build for the Test (Integration Test) subproject.
// This can also serve as an example standalone application setup.
// (See http://doc.akka.io/docs/akka/snapshot/scala/microkernel.html)
trait Test extends Build with Settings with Cs with Cmd {

  // top level Test project
  lazy val test = Project(id = "test", base = file("test")) aggregate (test_app, test_client)

  val testDependencies = Seq(
    akkaActor,
    akkaKernel,
    akkaRemote,
    typesafeConfig,
    scalaLogging,
    logback,
    csAkka,
    csApi,
    csCore,
    cmdCore,
    cmdAkka
  )

  lazy val defaultSettings = buildSettings ++ Seq(
    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
  )

  // Test subprojects with dependency information
  lazy val test_app = Project(
    id = "test-app",
    base = file("test/app"),
    settings = defaultSettings ++ distSettings ++ Seq(
      libraryDependencies ++= testDependencies,
      distJvmOptions in Dist := "-Xms256M -Xmx1024M",
      distBootClass in Dist := "org.tmt.csw.test.app.TestApp",
      outputDirectory in Dist := file("target/test-app")
    )
  ) dependsOn(cs_akka, cs_api, cs_core, cmd_core, cmd_akka)

  lazy val test_client = Project(
    id = "test-client",
    base = file("test/client"),
    settings = defaultSettings ++ distSettings ++ Seq(
      libraryDependencies ++= testDependencies,
      distJvmOptions in Dist := "-Xms256M -Xmx1024M",
      distBootClass in Dist := "org.tmt.csw.test.client.TestClient",
      outputDirectory in Dist := file("target/test-client")
    )
  ) dependsOn(cs_akka, cs_api, cs_core, cmd_core, cmd_akka)

}
